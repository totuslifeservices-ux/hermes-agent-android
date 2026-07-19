import { LiveKitRoomProps } from "@livekit/components-react";

export const livekitConfig = {
  url: process.env.NEXT_PUBLIC_LIVEKIT_URL || "ws://localhost:7880",
  apiKey: process.env.LIVEKIT_API_KEY || "devkey",
  apiSecret: process.env.LIVEKIT_API_SECRET || "devsecret",
};

export const e2eeConfig = {
  // LiveKit E2EE uses AES-256-GCM per participant
  // Key is derived from a shared passphrase known to all participants
  // In production, this should be exchanged via a secure side channel
  sharedKey: process.env.NEXT_PUBLIC_E2EE_KEY || "totus-telehealth-e2ee-dev-key-change-in-prod",
};

export function getRoomConnectionDetails(roomName: string, participantName: string) {
  // In production, the token should be generated server-side
  return {
    roomName,
    participantName,
    // Server-side token generation endpoint
    tokenEndpoint: `/api/rooms/token?room=${roomName}&name=${encodeURIComponent(participantName)}`,
  };
}
