"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  useMediaDevices,
  useMediaDeviceSelect,
} from "@livekit/components-react";
import {
  createLocalVideoTrack,
  createLocalAudioTrack,
  type LocalVideoTrack,
  type LocalAudioTrack,
} from "livekit-client";
import {
  Monitor,
  Volume2,
  CheckCircle2,
  AlertCircle,
  Camera,
  Mic,
} from "lucide-react";
import { cn } from "@/lib/utils";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface DeviceCheckProps {
  open: boolean;
  onClose: () => void;
  onReady: () => void;
}

type DeviceCheckStatus = "idle" | "testing" | "success" | "error";

/* ------------------------------------------------------------------ */
/*  Status badge                                                       */
/* ------------------------------------------------------------------ */

function StatusBadge({
  status,
  label,
}: {
  status: DeviceCheckStatus;
  label: string;
}) {
  const iconMap = {
    idle: null,
    testing: (
      <div className="size-4 animate-spin rounded-full border-2 border-primary/30 border-t-primary" />
    ),
    success: <CheckCircle2 className="size-4 text-green-500" />,
    error: <AlertCircle className="size-4 text-destructive" />,
  };

  const colorMap = {
    idle: "text-muted-foreground",
    testing: "text-primary",
    success: "text-green-600 dark:text-green-400",
    error: "text-destructive",
  };

  return (
    <div className="flex items-center gap-2">
      <span className="size-4">{iconMap[status]}</span>
      <span className={cn("text-sm font-medium", colorMap[status])}>
        {label}
      </span>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Device Check Modal                                                 */
/* ------------------------------------------------------------------ */

const overlayVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1 },
};

const panelVariants = {
  hidden: { opacity: 0, scale: 0.95, y: 10 },
  visible: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: 0.95, y: 10 },
};

export function DeviceCheck({ open, onClose, onReady }: DeviceCheckProps) {
  /* ---- device lists ---- */
  const videoDevices = useMediaDevices({ kind: "videoinput" });
  const audioInputDevices = useMediaDevices({ kind: "audioinput" });
  const audioOutputDevices = useMediaDevices({ kind: "audiooutput" });

  /* ---- selected devices ---- */
  const [videoDeviceId, setVideoDeviceId] = useState<string>("");
  const [audioInputId, setAudioInputId] = useState<string>("");
  const [audioOutputId, setAudioOutputId] = useState<string>("");

  /* ---- statuses ---- */
  const [cameraStatus, setCameraStatus] =
    useState<DeviceCheckStatus>("idle");
  const [micStatus, setMicStatus] = useState<DeviceCheckStatus>("idle");
  const [speakerStatus, setSpeakerStatus] =
    useState<DeviceCheckStatus>("idle");

  /* ---- live preview ---- */
  const videoRef = useRef<HTMLVideoElement>(null);
  const localVideoTrackRef = useRef<LocalVideoTrack | null>(null);
  const [micLevel, setMicLevel] = useState(0);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number>(0);

  /* ---- browser support warning ---- */
  const [permissionDenied, setPermissionDenied] = useState(false);

  /* ---- Initialise: select defaults ---- */
  useEffect(() => {
    if (videoDevices.length > 0 && !videoDeviceId) {
      setVideoDeviceId(videoDevices[0].deviceId);
    }
    if (audioInputDevices.length > 0 && !audioInputId) {
      setAudioInputId(audioInputDevices[0].deviceId);
    }
    if (audioOutputDevices.length > 0 && !audioOutputId) {
      setAudioOutputId(audioOutputDevices[0].deviceId);
    }
  }, [videoDevices, audioInputDevices, audioOutputDevices]);

  /* ---- Reset on open ---- */
  useEffect(() => {
    if (open) {
      setCameraStatus("idle");
      setMicStatus("idle");
      setSpeakerStatus("idle");
      setPermissionDenied(false);
      setMicLevel(0);
    }
  }, [open]);

  /* ---- Cleanup tracks on unmount ---- */
  useEffect(() => {
    return () => {
      localVideoTrackRef.current?.stop();
      localVideoTrackRef.current = null;
      cancelAnimationFrame(animationFrameRef.current);
      audioContextRef.current?.close();
    };
  }, []);

  /* ---- Test camera ---- */
  const testCamera = useCallback(async () => {
    setCameraStatus("testing");
    try {
      // Stop any existing track
      localVideoTrackRef.current?.stop();

      const track = await createLocalVideoTrack({
        deviceId: videoDeviceId || undefined,
        resolution: { width: 640, height: 480 },
      });

      localVideoTrackRef.current = track;

      if (videoRef.current) {
        track.attach(videoRef.current);
        await videoRef.current.play();
      }

      setCameraStatus("success");
    } catch (err: any) {
      console.error("Camera test failed:", err);
      if (
        err.name === "NotAllowedError" ||
        err.name === "PermissionDeniedError"
      ) {
        setPermissionDenied(true);
      }
      setCameraStatus("error");
    }
  }, [videoDeviceId]);

  /* ---- Test microphone ---- */
  const testMicrophone = useCallback(async () => {
    setMicStatus("testing");
    try {
      const track = await createLocalAudioTrack({
        deviceId: audioInputId || undefined,
        echoCancellation: false,
        noiseSuppression: false,
        autoGainControl: false,
      });

      // Set up audio level analysis
      const audioCtx = new AudioContext();
      audioContextRef.current = audioCtx;

      const source = audioCtx.createMediaStreamSource(
        new MediaStream([track.mediaStreamTrack]),
      );
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);
      analyserRef.current = analyser;

      const dataArray = new Uint8Array(analyser.frequencyBinCount);

      const updateLevel = () => {
        analyser.getByteTimeDomainData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          const value = (dataArray[i] - 128) / 128;
          sum += value * value;
        }
        const rms = Math.sqrt(sum / dataArray.length);
        setMicLevel(Math.min(rms * 3, 1));
        animationFrameRef.current = requestAnimationFrame(updateLevel);
      };
      updateLevel();

      setMicStatus("success");

      // Auto-stop after 3 seconds
      setTimeout(() => {
        cancelAnimationFrame(animationFrameRef.current);
        track.stop();
        audioCtx.close();
        setMicLevel(0);
      }, 3000);
    } catch (err: any) {
      console.error("Mic test failed:", err);
      if (
        err.name === "NotAllowedError" ||
        err.name === "PermissionDeniedError"
      ) {
        setPermissionDenied(true);
      }
      setMicStatus("error");
    }
  }, [audioInputId]);

  /* ---- Test speaker ---- */
  const testSpeakers = useCallback(async () => {
    setSpeakerStatus("testing");
    try {
      // Create a short audio tone to test output
      const audioCtx = new AudioContext();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      gain.gain.value = 0.3;
      osc.connect(gain);
      gain.connect(audioCtx.destination);

      osc.frequency.value = 440; // A4
      osc.start();
      osc.stop(audioCtx.currentTime + 1);

      await new Promise((resolve) => setTimeout(resolve, 1200));
      await audioCtx.close();

      setSpeakerStatus("success");
    } catch (err) {
      console.error("Speaker test failed:", err);
      setSpeakerStatus("error");
    }
  }, []);

  /* ---- Run all tests ---- */
  const runAllTests = useCallback(async () => {
    await testCamera();
    await testMicrophone();
    await testSpeakers();
  }, [testCamera, testMicrophone, testSpeakers]);

  /* ---- Run tests on mount ---- */
  useEffect(() => {
    if (open) {
      runAllTests();
    }
  }, [open]);

  /* ---- All passed? ---- */
  const allPassed =
    cameraStatus === "success" &&
    micStatus === "success" &&
    speakerStatus === "success";

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          variants={overlayVariants}
          initial="hidden"
          animate="visible"
          exit="hidden"
          onClick={onClose}
        >
          <motion.div
            className={cn(
              "relative mx-4 w-full max-w-lg overflow-hidden rounded-2xl",
              "bg-white/95 backdrop-blur-xl dark:bg-gray-900/95",
              "border border-white/20 shadow-2xl",
              "max-h-[90vh] overflow-y-auto",
            )}
            variants={panelVariants}
            initial="hidden"
            animate="visible"
            exit="exit"
            onClick={(e) => e.stopPropagation()}
          >
            {/* ---- Callout ---- */}
            <div className="flex items-start gap-3 bg-cyan-50 p-4 dark:bg-cyan-950/30">
              <AlertCircle className="mt-0.5 size-5 shrink-0 text-cyan-600 dark:text-cyan-400" />
              <p className="text-sm text-cyan-800 dark:text-cyan-300">
                Allow camera &amp; microphone access in your browser when
                prompted. This helps us ensure everything works before you
                join the call.
              </p>
            </div>

            {/* ---- Heading ---- */}
            <div className="border-b border-border px-4 py-3">
              <h2 className="text-lg font-semibold text-foreground">
                Device Check
              </h2>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Verify your camera, microphone, and speakers
              </p>
            </div>

            {/* ---- Content ---- */}
            <div className="space-y-5 p-4">
              {/* Camera preview + status */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Camera className="size-4 text-foreground/70" />
                    <span className="text-sm font-medium text-foreground">
                      Camera
                    </span>
                  </div>
                  <StatusBadge status={cameraStatus} label={statusLabel(cameraStatus, "Camera")} />
                </div>

                {/* Live preview */}
                <div className="relative overflow-hidden rounded-xl bg-black/10 dark:bg-black/30">
                  <video
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    className="h-48 w-full object-cover"
                  />
                  {cameraStatus === "idle" && (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <p className="text-xs text-muted-foreground">
                        Starting camera…
                      </p>
                    </div>
                  )}
                  {cameraStatus === "error" && (
                    <div className="absolute inset-0 flex items-center justify-center bg-black/40">
                      <p className="text-xs text-destructive">
                        Camera unavailable
                      </p>
                    </div>
                  )}
                </div>

                {/* Device selector */}
                {videoDevices.length > 0 && (
                  <select
                    value={videoDeviceId}
                    onChange={(e) => {
                      setVideoDeviceId(e.target.value);
                      setCameraStatus("idle");
                    }}
                    className={cn(
                      "w-full rounded-lg border border-border bg-muted/50 px-3 py-2 text-sm",
                      "text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40",
                    )}
                  >
                    {videoDevices.map((d) => (
                      <option key={d.deviceId} value={d.deviceId}>
                        {d.label || `Camera ${d.deviceId.slice(0, 8)}…`}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              {/* Microphone */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Mic className="size-4 text-foreground/70" />
                    <span className="text-sm font-medium text-foreground">
                      Microphone
                    </span>
                  </div>
                  <StatusBadge status={micStatus} label={statusLabel(micStatus, "Microphone")} />
                </div>

                {/* Level indicator */}
                <div className="h-2 overflow-hidden rounded-full bg-muted">
                  <motion.div
                    className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-teal-500"
                    animate={{ width: `${micLevel * 100}%` }}
                    transition={{ duration: 0.08 }}
                  />
                </div>

                {/* Device selector */}
                {audioInputDevices.length > 0 && (
                  <select
                    value={audioInputId}
                    onChange={(e) => {
                      setAudioInputId(e.target.value);
                      setMicStatus("idle");
                    }}
                    className={cn(
                      "w-full rounded-lg border border-border bg-muted/50 px-3 py-2 text-sm",
                      "text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40",
                    )}
                  >
                    {audioInputDevices.map((d) => (
                      <option key={d.deviceId} value={d.deviceId}>
                        {d.label || `Mic ${d.deviceId.slice(0, 8)}…`}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              {/* Speakers */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Volume2 className="size-4 text-foreground/70" />
                    <span className="text-sm font-medium text-foreground">
                      Speakers
                    </span>
                  </div>
                  <StatusBadge status={speakerStatus} label={statusLabel(speakerStatus, "Speakers")} />
                </div>

                {/* Device selector */}
                {audioOutputDevices.length > 0 && (
                  <select
                    value={audioOutputId}
                    onChange={(e) => setAudioOutputId(e.target.value)}
                    className={cn(
                      "w-full rounded-lg border border-border bg-muted/50 px-3 py-2 text-sm",
                      "text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40",
                    )}
                  >
                    {audioOutputDevices.map((d) => (
                      <option key={d.deviceId} value={d.deviceId}>
                        {d.label || `Speaker ${d.deviceId.slice(0, 8)}…`}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            </div>

            {/* ---- Actions ---- */}
            <div className="flex items-center justify-end gap-3 border-t border-border p-4">
              <button
                type="button"
                onClick={() => {
                  localVideoTrackRef.current?.stop();
                  localVideoTrackRef.current = null;
                  onClose();
                }}
                className={cn(
                  "cursor-pointer rounded-lg px-4 py-2 text-sm font-medium",
                  "text-muted-foreground hover:bg-muted",
                  "transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
                  "min-h-[44px]",
                )}
              >
                Skip
              </button>

              <button
                type="button"
                onClick={() => {
                  localVideoTrackRef.current?.stop();
                  localVideoTrackRef.current = null;
                  onReady();
                }}
                disabled={!allPassed}
                className={cn(
                  "cursor-pointer rounded-lg px-6 py-2 text-sm font-medium",
                  "bg-primary text-primary-foreground",
                  "hover:bg-primary/90 active:scale-[0.98]",
                  "transition-all duration-150",
                  "disabled:cursor-not-allowed disabled:opacity-40",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
                  "min-h-[44px]",
                )}
              >
                {allPassed ? "Ready to Join" : "Testing devices…"}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/* ------------------------------------------------------------------ */
/*  Helper                                                             */
/* ------------------------------------------------------------------ */

function statusLabel(status: DeviceCheckStatus, name: string): string {
  switch (status) {
    case "idle":
      return `Checking ${name}…`;
    case "testing":
      return `Testing ${name}…`;
    case "success":
      return `${name} OK`;
    case "error":
      return `${name} unavailable`;
  }
}
