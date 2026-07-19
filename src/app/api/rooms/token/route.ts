import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { livekitConfig, e2eeConfig } from "@/lib/livekit";

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const roomName = searchParams.get("room");
    const participantName = searchParams.get("name");
    const providerSlug = searchParams.get("provider");

    if (!roomName || !participantName) {
      return errorResponse("Room name and participant name are required", 400);
    }

    // For provider-side, require auth
    const isProvider = !!providerSlug;
    if (isProvider) {
      const { error } = await requireAuth();
      if (error) return error;
    }

    // Generate LiveKit token
    const at = new AccessToken(livekitConfig.apiKey, livekitConfig.apiSecret, {
      identity: participantName,
    });

    at.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
    });

    const token = at.toJwt();

    return successResponse({
      token,
      url: livekitConfig.url,
      roomName,
      participantName,
      e2ee: e2eeConfig.sharedKey || null,
    });
  } catch (error) {
    console.error("Token generation error:", error);
    return errorResponse("Failed to generate room token", 500);
  }
}
