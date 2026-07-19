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
    const { title } = body;

    // Generate a unique room slug
    const baseSlug = `${user.slug || user.id}-${Date.now()}`;
    let roomName = baseSlug;
    let slugSuffix = 1;
    while (await db.room.findUnique({ where: { roomName } })) {
      roomName = `${baseSlug}-${slugSuffix}`;
      slugSuffix++;
    }

    // Create the LiveKit room
    const roomServiceClient = new RoomServiceClient(
      livekitConfig.url,
      livekitConfig.apiKey,
      livekitConfig.apiSecret
    );

    try {
      await roomServiceClient.createRoom({
        name: roomName,
        emptyTimeout: 10 * 60, // 10 minutes
        maxParticipants: 2,
      });
    } catch (lkError) {
      console.error("LiveKit createRoom error:", lkError);
      // If LiveKit creation fails but the room already exists, continue
      const lkErrMsg =
        lkError instanceof Error ? lkError.message : String(lkError);
      if (!lkErrMsg.toLowerCase().includes("already exists")) {
        return errorResponse("Failed to create video room", 500);
      }
    }

    // Create Room record in DB
    const room = await db.room.create({
      data: {
        title: title || `${user.name || "Provider"}'s Room`,
        providerId: user.id as string,
        roomName,
        isActive: true,
        startedAt: new Date(),
        e2eeEnabled: true,
      },
    });

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id as string,
        action: "room.create",
        resource: "room",
        details: `Room created: ${roomName}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse({ room }, 201);
  } catch (error) {
    console.error("Room creation error:", error);
    return errorResponse("Failed to create room", 500);
  }
}
