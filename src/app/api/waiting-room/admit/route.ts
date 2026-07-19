import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { db } from "@/lib/db";
import { livekitConfig, e2eeConfig } from "@/lib/livekit";

export async function POST(request: NextRequest) {
  try {
    const { error, user } = await requireAuth();
    if (error) return error;

    const body = await request.json();
    const { entryId } = body;

    if (!entryId) {
      return errorResponse("Entry ID is required", 400);
    }

    // Find the waiting room entry with the room
    const entry = await db.waitingRoomEntry.findUnique({
      where: { id: entryId },
      include: {
        room: true,
      },
    });

    if (!entry) {
      return errorResponse("Waiting room entry not found", 404);
    }

    // Verify the provider owns the room
    if (entry.room.providerId !== user.id) {
      return errorResponse("You do not own this room", 403);
    }

    if (entry.status !== "waiting") {
      return errorResponse(
        `Entry is already ${entry.status}`,
        400
      );
    }

    // Update entry to admitted
    const updatedEntry = await db.waitingRoomEntry.update({
      where: { id: entryId },
      data: {
        status: "admitted",
        admittedAt: new Date(),
      },
    });

    // If the room is not active, activate it
    if (!entry.room.isActive) {
      await db.room.update({
        where: { id: entry.room.id },
        data: {
          isActive: true,
          startedAt: new Date(),
        },
      });
    }

    // Generate LiveKit token for the patient
    const at = new AccessToken(livekitConfig.apiKey, livekitConfig.apiSecret, {
      identity: entry.patientName,
    });

    at.addGrant({
      roomJoin: true,
      room: entry.room.roomName,
      canPublish: true,
      canSubscribe: true,
    });

    const token = at.toJwt();

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id as string,
        action: "waiting-room.admit",
        resource: "waiting_room_entry",
        details: `Patient ${entry.patientName} admitted to room ${entry.room.roomName}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse({
      entry: updatedEntry,
      token,
      roomName: entry.room.roomName,
      url: livekitConfig.url,
      e2ee: e2eeConfig.sharedKey || null,
    });
  } catch (error) {
    console.error("Waiting room admit error:", error);
    return errorResponse("Failed to admit patient", 500);
  }
}
