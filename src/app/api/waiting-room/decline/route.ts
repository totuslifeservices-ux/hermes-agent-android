import { NextRequest } from "next/server";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { db } from "@/lib/db";

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

    // Update entry to declined
    const updatedEntry = await db.waitingRoomEntry.update({
      where: { id: entryId },
      data: {
        status: "declined",
        leftAt: new Date(),
      },
    });

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id as string,
        action: "waiting-room.decline",
        resource: "waiting_room_entry",
        details: `Patient ${entry.patientName} declined from room ${entry.room.roomName}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse({ entry: updatedEntry });
  } catch (error) {
    console.error("Waiting room decline error:", error);
    return errorResponse("Failed to decline patient", 500);
  }
}
