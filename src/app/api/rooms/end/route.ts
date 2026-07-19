import { NextRequest } from "next/server";
import { RoomServiceClient } from "livekit-server-sdk";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { db } from "@/lib/db";
import { livekitConfig } from "@/lib/livekit";

export async function POST(request: NextRequest) {
  try {
    const { error, user } = await requireAuth();
    if (error) return error;

    const body = await request.json();
    const { roomId } = body;

    if (!roomId) {
      return errorResponse("Room ID is required", 400);
    }

    // Find the room and verify ownership
    const room = await db.room.findUnique({
      where: { id: roomId },
      include: {
        waitingRoom: {
          where: { status: { in: ["waiting", "admitted"] } },
        },
      },
    });

    if (!room) {
      return errorResponse("Room not found", 404);
    }

    if (room.providerId !== user.id) {
      return errorResponse("You do not own this room", 403);
    }

    if (!room.isActive) {
      return errorResponse("Room is already ended", 400);
    }

    // Calculate duration
    const now = new Date();
    const startedAt = room.startedAt || room.createdAt;
    const durationMs = now.getTime() - startedAt.getTime();
    const durationSeconds = Math.floor(durationMs / 1000);

    // Attempt to delete the LiveKit room (best-effort)
    try {
      const roomServiceClient = new RoomServiceClient(
        livekitConfig.url,
        livekitConfig.apiKey,
        livekitConfig.apiSecret
      );
      await roomServiceClient.deleteRoom(room.roomName);
    } catch (lkError) {
      console.warn("LiveKit deleteRoom warning (non-fatal):", lkError);
    }

    // Update Room record
    const updatedRoom = await db.room.update({
      where: { id: roomId },
      data: {
        isActive: false,
        endedAt: now,
        duration: durationSeconds,
      },
    });

    // Create SessionHistory record
    const admittedEntries = room.waitingRoom.filter(
      (e) => e.status === "admitted"
    );
    const patientName =
      admittedEntries.length > 0
        ? admittedEntries[0].patientName
        : null;
    const patientEmail =
      admittedEntries.length > 0
        ? admittedEntries[0].patientEmail
        : null;

    await db.sessionHistory.create({
      data: {
        providerId: user.id as string,
        roomName: room.roomName,
        patientName,
        patientEmail,
        date: now,
        duration: durationSeconds,
        hasRecording: false,
        consentGiven: true,
      },
    });

    // Mark waiting room entries as left
    for (const entry of room.waitingRoom) {
      await db.waitingRoomEntry.update({
        where: { id: entry.id },
        data: { leftAt: now },
      });
    }

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id as string,
        action: "room.end",
        resource: "room",
        details: `Room ended: ${room.roomName}, duration: ${durationSeconds}s`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse({ room: updatedRoom });
  } catch (error) {
    console.error("Room end error:", error);
    return errorResponse("Failed to end room", 500);
  }
}
