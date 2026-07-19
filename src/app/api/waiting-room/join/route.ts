import { NextRequest } from "next/server";
import { successResponse, errorResponse } from "@/lib/api-utils";
import { db } from "@/lib/db";

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { slug, patientName, patientEmail } = body;

    if (!slug || !patientName) {
      return errorResponse("Provider slug and patient name are required", 400);
    }

    // Find provider by slug
    const provider = await db.user.findUnique({
      where: { slug },
    });

    if (!provider) {
      return errorResponse("Provider not found", 404);
    }

    if (!provider.isActive) {
      return errorResponse("Provider account is not active", 403);
    }

    // Find or create an active room for the provider
    let room = await db.room.findFirst({
      where: {
        providerId: provider.id,
        isActive: true,
      },
      orderBy: { createdAt: "desc" },
    });

    if (!room) {
      // Create a new room
      const roomName = `${slug}-${Date.now()}`;
      room = await db.room.create({
        data: {
          title: `${provider.name || "Provider"}'s Room`,
          providerId: provider.id,
          roomName,
          isActive: false,
          e2eeEnabled: true,
        },
      });
    }

    // Get next position in waiting room
    const lastEntry = await db.waitingRoomEntry.findFirst({
      where: { roomId: room.id, status: "waiting" },
      orderBy: { position: "desc" },
    });
    const nextPosition = (lastEntry?.position ?? 0) + 1;

    // Check if patient is already in the waiting room
    const existingEntry = await db.waitingRoomEntry.findFirst({
      where: {
        roomId: room.id,
        patientName,
        status: "waiting",
      },
    });

    if (existingEntry) {
      return successResponse({
        entry: existingEntry,
        roomName: room.roomName,
        alreadyJoined: true,
      });
    }

    // Create waiting room entry
    const entry = await db.waitingRoomEntry.create({
      data: {
        roomId: room.id,
        patientName,
        patientEmail: patientEmail || null,
        status: "waiting",
        position: nextPosition,
      },
    });

    // Audit log
    await db.auditLog.create({
      data: {
        userId: provider.id,
        action: "waiting-room.join",
        resource: "waiting_room_entry",
        details: `Patient ${patientName} joined waiting room for ${slug}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse(
      { entry, roomName: room.roomName, alreadyJoined: false },
      201
    );
  } catch (error) {
    console.error("Waiting room join error:", error);
    return errorResponse("Failed to join waiting room", 500);
  }
}
