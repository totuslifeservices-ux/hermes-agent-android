"use client";

import { useEffect, useState, useCallback } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  LiveKitRoom,
  useTracks,
  ParticipantTile,
  RoomAudioRenderer,
  TrackLoop,
} from "@livekit/components-react";
import {
  Track,
  type RoomOptions,
  ExternalE2EEKeyProvider,
} from "livekit-client";
import "@livekit/components-styles";
import { e2eeConfig } from "@/lib/livekit";
import { cn } from "@/lib/utils";
import { VideoControls } from "./video-controls";
import { VideoChat } from "./video-chat";
import { VirtualBackground } from "./virtual-background";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface VideoCallProps {
  roomName: string;
  token: string;
  onLeave: () => void;
}

/* ------------------------------------------------------------------ */
/*  Inner grid — renders participant video tiles                       */
/* ------------------------------------------------------------------ */

function ParticipantGrid() {
  const tracks = useTracks(
    [Track.Source.Camera, Track.Source.ScreenShare],
    { onlySubscribed: false },
  );

  if (tracks.length === 0) {
    return (
      <div className="flex h-full w-full items-center justify-center">
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-muted-foreground text-sm"
        >
          Waiting for participants…
        </motion.p>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "grid h-full w-full auto-rows-fr gap-2 p-2",
        tracks.length === 1
          ? "grid-cols-1"
          : tracks.length <= 4
            ? "grid-cols-2"
            : "grid-cols-3",
      )}
    >
      <TrackLoop tracks={tracks}>
        <ParticipantTile className="overflow-hidden rounded-xl" />
      </TrackLoop>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Main video call component                                          */
/* ------------------------------------------------------------------ */

export function VideoCall({ roomName, token, onLeave }: VideoCallProps) {
  const [chatOpen, setChatOpen] = useState(false);
  const [backgroundOpen, setBackgroundOpen] = useState(false);
  const [roomOptions, setRoomOptions] = useState<RoomOptions | undefined>();
  const [connectionError, setConnectionError] = useState<string | null>(null);

  const serverUrl =
    process.env.NEXT_PUBLIC_LIVEKIT_URL ?? "ws://localhost:7880";

  /* ---- Set up E2EE and room options ---- */
  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      try {
        const keyProvider = new ExternalE2EEKeyProvider();
        await keyProvider.setKey(e2eeConfig.sharedKey);
        const worker = new Worker(
          new URL(
            "livekit-client/dist/livekit-client.e2ee.worker.js",
            import.meta.url,
          ),
        );
        if (!cancelled) {
          setRoomOptions({
            encryption: { keyProvider, worker },
            videoCaptureDefaults: { resolution: { width: 1280, height: 720 } },
            audioCaptureDefaults: {
              autoGainControl: true,
              echoCancellation: true,
              noiseSuppression: true,
            },
            adaptiveStream: true,
            dynacast: true,
          });
        }
      } catch (err) {
        console.error("Failed to initialise E2EE:", err);
        if (!cancelled) {
          setConnectionError(
            "Failed to set up end-to-end encryption. Please try again.",
          );
        }
      }
    };
    init();
    return () => { cancelled = true; };
  }, []);

  /* ---- callbacks ---- */
  const handleDisconnect = useCallback(() => {
    setChatOpen(false);
    setBackgroundOpen(false);
    onLeave();
  }, [onLeave]);

  const handleEncryptionError = useCallback((error: Error) => {
    console.error("Encryption error:", error);
    setConnectionError(
      "An encryption error occurred. The call may not be secure.",
    );
  }, []);

  /* ---- Loading state ---- */
  if (!roomOptions) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-black">
        <div className="flex flex-col items-center gap-4">
          <div className="size-10 animate-spin rounded-full border-4 border-primary/30 border-t-primary" />
          <p className="text-sm text-muted-foreground">
            Setting up end-to-end encryption…
          </p>
        </div>
      </div>
    );
  }

  /* ---- Error state ---- */
  if (connectionError) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-black">
        <div className="mx-4 max-w-sm rounded-2xl bg-destructive/10 p-6 text-center">
          <p className="mb-4 text-sm text-destructive">{connectionError}</p>
          <button
            type="button"
            onClick={onLeave}
            className="cursor-pointer rounded-lg bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition-colors hover:bg-destructive/90"
          >
            Return to waiting room
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="relative h-full w-full overflow-hidden bg-black">
      <LiveKitRoom
        serverUrl={serverUrl}
        token={token}
        connect={true}
        onDisconnected={handleDisconnect}
        onEncryptionError={handleEncryptionError}
        options={roomOptions}
        className="h-full w-full"
      >
        {/* Required for audio to work with E2EE */}
        <RoomAudioRenderer />

        {/* Participant video grid */}
        <ParticipantGrid />

        {/* Custom control bar pinned at the bottom */}
        <VideoControls
          chatOpen={chatOpen}
          onChatToggle={() => setChatOpen((v) => !v)}
          onBackgroundToggle={() => setBackgroundOpen((v) => !v)}
          onLeave={onLeave}
        />

        {/* Slide-in chat panel */}
        <AnimatePresence>
          {chatOpen && <VideoChat onClose={() => setChatOpen(false)} />}
        </AnimatePresence>

        {/* Virtual background selector overlay */}
        <AnimatePresence>
          {backgroundOpen && (
            <VirtualBackground onClose={() => setBackgroundOpen(false)} />
          )}
        </AnimatePresence>
      </LiveKitRoom>
    </div>
  );
}
