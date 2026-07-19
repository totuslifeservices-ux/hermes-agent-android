"use client";

import { useCallback } from "react";
import {
  useLocalParticipant,
  useMediaDevices,
  useMediaDeviceSelect,
  useRoomContext,
} from "@livekit/components-react";
import {
  Mic,
  MicOff,
  Video,
  VideoOff,
  Monitor,
  MonitorOff,
  MessageSquare,
  PhoneOff,
  Clapperboard,
} from "lucide-react";
import { cn } from "@/lib/utils";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface VideoControlsProps {
  chatOpen: boolean;
  onChatToggle: () => void;
  onBackgroundToggle: () => void;
  onLeave: () => void;
}

/* ------------------------------------------------------------------ */
/*  Custom control bar                                                 */
/* ------------------------------------------------------------------ */

function ControlButton({
  icon: Icon,
  label,
  active,
  danger,
  onClick,
}: {
  icon: React.ElementType;
  label: string;
  active?: boolean;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-1 rounded-xl p-3 text-xs font-medium transition-all duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
        // elder-friendly: ≥44px touch target
        "min-w-[56px] min-h-[56px]",
        danger
          ? "bg-destructive/20 text-destructive hover:bg-destructive/30 active:scale-95"
          : active
            ? "bg-primary/15 text-primary hover:bg-primary/25"
            : "text-foreground/70 hover:bg-white/10 hover:text-foreground",
      )}
    >
      <Icon className="size-5" />
      <span className="truncate text-[10px] leading-tight">{label}</span>
    </button>
  );
}

export function VideoControls({
  chatOpen,
  onChatToggle,
  onBackgroundToggle,
  onLeave,
}: VideoControlsProps) {
  const { localParticipant, isMicrophoneEnabled, isCameraEnabled } =
    useLocalParticipant();

  /* ---- mic toggle ---- */
  const toggleMic = useCallback(() => {
    localParticipant?.setMicrophoneEnabled(!isMicrophoneEnabled);
  }, [localParticipant, isMicrophoneEnabled]);

  /* ---- camera toggle ---- */
  const toggleCamera = useCallback(() => {
    localParticipant?.setCameraEnabled(!isCameraEnabled);
  }, [localParticipant, isCameraEnabled]);

  /* ---- screen share toggle ---- */
  const toggleScreenShare = useCallback(async () => {
    if (!localParticipant) return;
    const isScreenSharing =
      localParticipant.isScreenShareEnabled;
    await localParticipant.setScreenShareEnabled(!isScreenSharing);
  }, [localParticipant]);

  /* ---- leave ---- */
  const room = useRoomContext();
  const handleLeave = useCallback(() => {
    room?.disconnect();
    onLeave();
  }, [room, onLeave]);

  return (
    <div
      className={cn(
        // pinned to bottom
        "absolute bottom-0 left-0 right-0 z-30",
        // glass-morphism
        "bg-gradient-to-t from-black/70 via-black/40 to-transparent",
        "backdrop-blur-md",
        "px-4 pb-safe pb-4 pt-12",
      )}
    >
      <div className="mx-auto flex max-w-xl items-center justify-center gap-2 rounded-2xl bg-white/10 p-2 backdrop-blur-xl dark:bg-black/30">
        {/* Mic */}
        <ControlButton
          icon={isMicrophoneEnabled ? Mic : MicOff}
          label={isMicrophoneEnabled ? "Mute" : "Unmute"}
          active={!isMicrophoneEnabled}
          onClick={toggleMic}
        />

        {/* Camera */}
        <ControlButton
          icon={isCameraEnabled ? Video : VideoOff}
          label={isCameraEnabled ? "Stop" : "Camera"}
          active={!isCameraEnabled}
          onClick={toggleCamera}
        />

        {/* Screen share */}
        <ControlButton
          icon={Monitor}
          label="Share"
          onClick={toggleScreenShare}
        />

        {/* Virtual background */}
        <ControlButton
          icon={Clapperboard}
          label="Bg"
          onClick={onBackgroundToggle}
        />

        {/* Chat */}
        <ControlButton
          icon={MessageSquare}
          label="Chat"
          active={chatOpen}
          onClick={onChatToggle}
        />

        {/* Divider */}
        <div className="mx-1 h-8 w-px bg-white/20" />

        {/* Leave / End call */}
        <ControlButton
          icon={PhoneOff}
          label="Leave"
          danger
          onClick={handleLeave}
        />
      </div>
    </div>
  );
}
