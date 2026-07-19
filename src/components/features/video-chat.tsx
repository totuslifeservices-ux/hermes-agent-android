"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { useChat } from "@livekit/components-react";
import { Send, X } from "lucide-react";
import { cn } from "@/lib/utils";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface VideoChatProps {
  onClose: () => void;
}

/* ------------------------------------------------------------------ */
/*  Slide-in chat panel                                                */
/* ------------------------------------------------------------------ */

const panelVariants = {
  hidden: { x: "100%", opacity: 0 },
  visible: { x: 0, opacity: 1 },
  exit: { x: "100%", opacity: 0 },
};

export function VideoChat({ onClose }: VideoChatProps) {
  const { chatMessages, send, isSending } = useChat();
  const [inputValue, setInputValue] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  /* ---- auto-scroll to latest message ---- */
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatMessages]);

  /* ---- focus input on open ---- */
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  /* ---- send message ---- */
  const handleSend = async () => {
    const text = inputValue.trim();
    if (!text || isSending) return;
    try {
      await send(text);
      setInputValue("");
    } catch (err) {
      console.error("Failed to send chat message:", err);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <motion.div
      className="absolute right-0 top-0 z-30 flex h-full w-full max-w-sm flex-col border-l border-white/10 bg-background/95 backdrop-blur-2xl dark:bg-gray-950/95"
      variants={panelVariants}
      initial="hidden"
      animate="visible"
      exit="exit"
      transition={{ type: "spring", damping: 28, stiffness: 300 }}
    >
      {/* ---- Header ---- */}
      <div className="flex shrink-0 items-center justify-between border-b border-border px-4 py-3">
        <h2 className="text-sm font-semibold text-foreground">In-call Chat</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close chat"
          className={cn(
            "flex size-8 cursor-pointer items-center justify-center rounded-full",
            "text-muted-foreground hover:bg-muted hover:text-foreground",
            "transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
          )}
        >
          <X className="size-4" />
        </button>
      </div>

      {/* ---- Messages ---- */}
      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        {chatMessages.length === 0 ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-center text-sm text-muted-foreground">
              No messages yet.
              <br />
              Start the conversation!
            </p>
          </div>
        ) : (
          chatMessages.map((msg, idx) => (
            <div
              key={msg.timestamp ?? idx}
              className={cn(
                "rounded-xl px-3 py-2 text-sm max-w-[85%]",
                msg.from?.identity === "local"
                  ? "ml-auto bg-primary text-primary-foreground"
                  : "mr-auto bg-muted text-foreground",
              )}
            >
              {/* Sender name (not shown for own messages) */}
              {msg.from?.identity !== "local" && (
                <p className="mb-0.5 text-[11px] font-semibold text-primary">
                  {msg.from?.name ?? msg.from?.identity ?? "Unknown"}
                </p>
              )}

              {/* Message content */}
              <p className="break-words">{msg.message}</p>

              {/* Timestamp */}
              {msg.timestamp && (
                <p
                  className={cn(
                    "mt-1 text-[10px]",
                    msg.from?.identity === "local"
                      ? "text-primary-foreground/60"
                      : "text-muted-foreground",
                  )}
                >
                  {formatTime(msg.timestamp)}
                </p>
              )}
            </div>
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* ---- Input bar ---- */}
      <div className="flex shrink-0 items-center gap-2 border-t border-border p-3">
        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message…"
          disabled={isSending}
          className={cn(
            "flex-1 rounded-xl border border-border bg-muted/50 px-3 py-2.5 text-sm",
            "text-foreground placeholder:text-muted-foreground",
            "focus:outline-none focus:ring-2 focus:ring-primary/40",
            "disabled:opacity-50",
            // elder-friendly: larger input
            "min-h-[44px]",
          )}
        />
        <button
          type="button"
          onClick={handleSend}
          disabled={!inputValue.trim() || isSending}
          aria-label="Send message"
          className={cn(
            "flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-xl",
            "bg-primary text-primary-foreground",
            "hover:bg-primary/90 active:scale-95",
            "transition-all duration-150",
            "disabled:cursor-not-allowed disabled:opacity-40",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60",
          )}
        >
          <Send className="size-4" />
        </button>
      </div>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/*  Helper                                                             */
/* ------------------------------------------------------------------ */

function formatTime(timestamp: number): string {
  const d = new Date(timestamp);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
