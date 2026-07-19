import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { successResponse, errorResponse } from "@/lib/api-utils";
import { db } from "@/lib/db";
import { livekitConfig, e2eeConfig } from "@/lib/livekit";

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const slug = searchParams.get("slug");
    const entryId = searchParams.get("entryId");

    if (!slug || !entryId) {
      return errorResponse("Provider slug and entry ID are required", 400);
    }

    // Find the provider
    const provider = await db.user.findUnique({
      where: { slug },
    });

    if (!provider) {
      return errorResponse("Provider not found", 404);
    }

    // Find the waiting room entry
    const entry = await db.waitingRoomEntry.findUnique({
      where: { id: entryId },
      include: {
        room: true,
      },
    });

    if (!entry) {
      return errorResponse("Waiting room entry not found", 404);
    }

    // Ensure the entry belongs to this provider
    if (entry.room.providerId !== provider.id) {
      return errorResponse("Entry does not belong to this provider", 403);
    }

    // Check if status has changed
    if (entry.status === "waiting") {
      return successResponse({
        status: "waiting",
        roomName: entry.room.roomName,
        token: null,
      });
    }

    // If admitted, generate a token for the patient
    if (entry.status === "admitted") {
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

      return successResponse({
        status: "admitted",
        roomName: entry.room.roomName,
        token,
        url: livekitConfig.url,
        e2ee: e2eeConfig.sharedKey || null,
      });
    }

    // Declined or other terminal status
    return successResponse({
      status: entry.status,
      roomName: entry.room.roomName,
      token: null,
    });
  } catch (error) {
    console.error("Waiting room poll error:", error);
    return errorResponse("Failed to check waiting room status", 500);
  }
}
