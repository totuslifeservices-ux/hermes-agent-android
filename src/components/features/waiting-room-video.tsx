"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { VideoCall } from "./video-call";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface WaitingRoomVideoProps {
  roomName: string;
  token: string;
  /** Called once the participant is admitted into the actual call */
  onAdmitted: () => void;
  /** Called if the participant decides to leave the waiting room */
  onLeave: () => void;
}

type ConnectionStage =
  | "connecting"
  | "checking-devices"
  | "encrypting"
  | "admitted";

/* ------------------------------------------------------------------ */
/*  Connection status messages                                         */
/* ------------------------------------------------------------------ */

const STAGE_LABELS: Record<ConnectionStage, string> = {
  connecting: "Preparing your call…",
  "checking-devices": "Checking devices…",
  encrypting: "Establishing secure connection…",
  admitted: "You're being connected!",
};

const STAGE_INDICATORS: Record<ConnectionStage, string[]> = {
  connecting: ["Connecting to server"],
  "checking-devices": ["Verifying camera", "Testing microphone"],
  encrypting: ["Setting up encryption", "Securing data stream"],
  admitted: ["Ready to join"],
};

/* ------------------------------------------------------------------ */
/*  Animated pulsing Totus logo                                        */
/* ------------------------------------------------------------------ */

function TotusLogo() {
  return (
    <motion.div
      className="relative flex items-center justify-center"
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ duration: 0.6, ease: "easeOut" }}
    >
      {/* Outer pulse ring */}
      <motion.div
        className="absolute size-32 rounded-full border-2 border-cyan-400/40"
        animate={{
          scale: [1, 1.3, 1],
          opacity: [0.5, 0, 0.5],
        }}
        transition={{
          duration: 2.5,
          repeat: Infinity,
          ease: "easeInOut",
        }}
      />
      {/* Middle pulse ring */}
      <motion.div
        className="absolute size-24 rounded-full border-2 border-teal-400/30"
        animate={{
          scale: [1, 1.25, 1],
          opacity: [0.4, 0, 0.4],
        }}
        transition={{
          duration: 2.5,
          repeat: Infinity,
          ease: "easeInOut",
          delay: 0.5,
        }}
      />
      {/* Inner ring */}
      <motion.div
        className="absolute size-16 rounded-full border border-cyan-300/20"
        animate={{
          scale: [1, 1.1, 1],
        }}
        transition={{
          duration: 2,
          repeat: Infinity,
          ease: "easeInOut",
          delay: 1,
        }}
      />
      {/* Logo text */}
      <div className="relative z-10 flex size-16 items-center justify-center rounded-full bg-gradient-to-br from-cyan-500 to-teal-600 shadow-lg shadow-cyan-500/30">
        <span className="text-2xl font-bold tracking-tight text-white">
          T
        </span>
      </div>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Connection stage indicator                                         */
/* ------------------------------------------------------------------ */

function StageIndicator({
  items,
  active,
}: {
  items: string[];
  active: boolean;
}) {
  return (
    <div className="space-y-2">
      {items.map((item, idx) => (
        <motion.div
          key={item}
          className="flex items-center gap-2"
          initial={{ opacity: 0, x: -8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: idx * 0.15, duration: 0.3 }}
        >
          <motion.div
            className={cn(
              "size-2 rounded-full",
              active ? "bg-cyan-400" : "bg-muted-foreground/30",
            )}
            animate={active ? { opacity: [0.4, 1, 0.4] } : undefined}
            transition={
              active
                ? { duration: 1.5, repeat: Infinity, ease: "easeInOut" }
                : undefined
            }
          />
          <span
            className={cn(
              "text-sm",
              active ? "text-foreground" : "text-muted-foreground/50",
            )}
          >
            {item}
          </span>
        </motion.div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Waiting Room                                                       */
/* ------------------------------------------------------------------ */

const fadeSlideUp = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -12 },
};

export function WaitingRoomVideo({
  roomName,
  token,
  onAdmitted,
  onLeave,
}: WaitingRoomVideoProps) {
  const [stage, setStage] = useState<ConnectionStage>("connecting");
  const [admitted, setAdmitted] = useState(false);

  /* ---- Simulate stages (in prod these are driven by LiveKit events) ---- */
  useEffect(() => {
    const t1 = setTimeout(() => setStage("checking-devices"), 1200);
    const t2 = setTimeout(() => setStage("encrypting"), 2600);

    // Simulate admission after some time
    // In production, this would be triggered by a "participant admitted" event
    const t3 = setTimeout(() => {
      setStage("admitted");
      setAdmitted(true);
    }, 4000);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, []);

  /* ---- Once admitted, show the video call ---- */
  if (admitted) {
    return (
      <VideoCall
        roomName={roomName}
        token={token}
        onLeave={onLeave}
      />
    );
  }

  return (
    <div className="relative flex h-full w-full flex-col items-center justify-center bg-gradient-to-b from-gray-950 via-gray-900 to-black px-6 dark:from-black dark:via-gray-950 dark:to-gray-900">
      {/* Decorative gradient blobs */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 right-1/4 size-80 rounded-full bg-cyan-500/10 blur-[100px]" />
        <div className="absolute -bottom-40 left-1/4 size-80 rounded-full bg-teal-500/10 blur-[100px]" />
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={stage}
          className="relative z-10 flex flex-col items-center gap-8"
          variants={fadeSlideUp}
          initial="hidden"
          animate="visible"
          exit="exit"
          transition={{ duration: 0.4 }}
        >
          {/* Animated logo */}
          <TotusLogo />

          {/* Stage label */}
          <motion.h1
            className="text-center text-2xl font-semibold text-white"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.2 }}
          >
            {STAGE_LABELS[stage]}
          </motion.h1>

          {/* Stage indicators */}
          <StageIndicator
            items={STAGE_INDICATORS[stage]}
            active={true}
          />

          {/* Leave button */}
          <motion.button
            type="button"
            onClick={onLeave}
            className={cn(
              "mt-4 cursor-pointer rounded-xl border border-white/20 px-6 py-3 text-sm font-medium text-white/70",
              "hover:bg-white/10 hover:text-white",
              "transition-all duration-200",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
              // elder-friendly touch target
              "min-h-[48px] min-w-[120px]",
            )}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 2 }}
          >
            Leave waiting room
          </motion.button>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
