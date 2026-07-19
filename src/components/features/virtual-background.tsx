"use client";

import { useCallback, useState } from "react";
import { motion } from "framer-motion";
import {
  BackgroundBlur,
  VirtualBackground as VBProcessor,
  supportsBackgroundProcessors,
} from "@livekit/track-processors";
import { useLocalParticipant } from "@livekit/components-react";
import { Track } from "livekit-client";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface VirtualBackgroundProps {
  onClose: () => void;
}

interface BackgroundOption {
  id: string;
  label: string;
  previewClass: string;
  /** blur radius in px (blur option) */
  blurRadius?: number;
  /** solid hex colour */
  color?: string;
}

/* ------------------------------------------------------------------ */
/*  Background options                                                 */
/* ------------------------------------------------------------------ */

const BACKGROUND_OPTIONS: BackgroundOption[] = [
  {
    id: "none",
    label: "None",
    previewClass:
      "bg-gradient-to-br from-gray-100 to-gray-300 dark:from-gray-700 dark:to-gray-900",
  },
  {
    id: "blur",
    label: "Blur",
    previewClass: "backdrop-blur-lg bg-white/20",
    blurRadius: 10,
  },
  {
    id: "teal",
    label: "Teal",
    previewClass: "bg-gradient-to-br from-teal-400 to-cyan-600",
    color: "#0d9488",
  },
  {
    id: "ocean",
    label: "Ocean",
    previewClass: "bg-gradient-to-br from-blue-500 to-cyan-400",
    color: "#06b6d4",
  },
  {
    id: "sunset",
    label: "Sunset",
    previewClass: "bg-gradient-to-br from-orange-400 to-rose-500",
    color: "#f97316",
  },
  {
    id: "forest",
    label: "Forest",
    previewClass: "bg-gradient-to-br from-emerald-600 to-green-400",
    color: "#059669",
  },
  {
    id: "lavender",
    label: "Lavender",
    previewClass: "bg-gradient-to-br from-purple-500 to-pink-400",
    color: "#a855f7",
  },
];

/* ------------------------------------------------------------------ */
/*  Background option card                                             */
/* ------------------------------------------------------------------ */

function BackgroundCard({
  option,
  selected,
  onSelect,
}: {
  option: BackgroundOption;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={option.label}
      title={option.label}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-2 rounded-xl p-3 transition-all duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
        "min-w-[80px]",
        selected
          ? "ring-2 ring-primary bg-primary/10"
          : "hover:bg-white/10 dark:hover:bg-white/5",
      )}
    >
      {/* Preview thumbnail */}
      <div
        className={cn(
          "h-14 w-20 overflow-hidden rounded-lg border border-white/20 shadow-sm",
          option.previewClass,
        )}
      />
      <span className="text-xs font-medium text-foreground/80">
        {option.label}
      </span>
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  Virtual Background Overlay                                         */
/* ------------------------------------------------------------------ */

const overlayVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1 },
};

const panelVariants = {
  hidden: { opacity: 0, scale: 0.92, y: 20 },
  visible: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: 0.92, y: 20 },
};

export function VirtualBackground({ onClose }: VirtualBackgroundProps) {
  const { localParticipant } = useLocalParticipant();
  const [selectedId, setSelectedId] = useState("none");
  const [applying, setApplying] = useState(false);

  const selectedOption =
    BACKGROUND_OPTIONS.find((o) => o.id === selectedId) ??
    BACKGROUND_OPTIONS[0];

  const applyBackground = useCallback(
    async (option: BackgroundOption) => {
      if (!localParticipant) return;
      setApplying(true);

      try {
        const cameraPub = localParticipant.getTrackPublication(Track.Source.Camera);
        const videoTrack = cameraPub?.videoTrack;
        if (!videoTrack) {
          console.warn("No camera track available");
          return;
        }

        // Check if browser supports background processing
        if (!supportsBackgroundProcessors()) {
          console.warn("Background processors not supported in this browser");
          // Still update the UI selection
          setSelectedId(option.id);
          return;
        }

        if (option.blurRadius) {
          // Apply blur
          const processor = BackgroundBlur(option.blurRadius);
          await videoTrack.setProcessor(processor);
        } else if (option.color) {
          // Apply solid colour background via VirtualBackground processor
          const canvas = document.createElement("canvas");
          canvas.width = 640;
          canvas.height = 480;
          const ctx = canvas.getContext("2d");
          if (ctx) {
            ctx.fillStyle = option.color;
            ctx.fillRect(0, 0, 640, 480);
          }
          const imageUrl = canvas.toDataURL();
          try {
            const processor = VBProcessor(imageUrl);
            await videoTrack.setProcessor(processor);
          } catch {
            // Fallback: just show the colour without processor
            await videoTrack.stopProcessor();
          }
        } else {
          // "None" — remove any active processor
          await videoTrack.stopProcessor();
        }

        setSelectedId(option.id);
      } catch (error) {
        console.error("Failed to apply background:", error);
      } finally {
        setApplying(false);
      }
    },
    [localParticipant],
  );

  return (
    <motion.div
      className="absolute inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      variants={overlayVariants}
      initial="hidden"
      animate="visible"
      exit="hidden"
      onClick={onClose}
    >
      <motion.div
        className={cn(
          "relative mx-4 w-full max-w-md overflow-hidden rounded-2xl",
          "bg-white/95 backdrop-blur-xl dark:bg-gray-900/95",
          "border border-white/20 shadow-2xl",
        )}
        variants={panelVariants}
        initial="hidden"
        animate="visible"
        exit="exit"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border p-4">
          <h2 className="text-lg font-semibold text-foreground">
            Virtual Background
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className={cn(
              "flex size-8 cursor-pointer items-center justify-center rounded-full",
              "text-muted-foreground hover:bg-muted hover:text-foreground",
              "transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
            )}
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Options grid */}
        <div className="grid grid-cols-4 gap-2 p-4">
          {BACKGROUND_OPTIONS.map((option) => (
            <BackgroundCard
              key={option.id}
              option={option}
              selected={selectedId === option.id}
              onSelect={() => applyBackground(option)}
            />
          ))}
        </div>

        {/* Status / selected label */}
        <div className="px-4 pb-4">
          {applying ? (
            <p className="text-center text-xs text-muted-foreground">
              Applying…
            </p>
          ) : (
            <p className="text-center text-xs text-muted-foreground">
              Selected:{" "}
              <span className="font-medium text-foreground">
                {selectedOption.label}
              </span>
            </p>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}
