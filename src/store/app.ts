import { create } from "zustand";

interface AppState {
  // Theme
  darkMode: boolean;
  toggleDarkMode: () => void;

  // Locale
  locale: "en" | "fr";
  setLocale: (locale: "en" | "fr") => void;

  // Video call
  isInCall: boolean;
  setInCall: (inCall: boolean) => void;
  callParticipantCount: number;
  setCallParticipantCount: (count: number) => void;

  // Waiting room
  waitingPatientCount: number;
  setWaitingPatientCount: (count: number) => void;
}

export const useAppStore = create<AppState>((set) => ({
  // Theme
  darkMode: false,
  toggleDarkMode: () => set((s) => ({ darkMode: !s.darkMode })),

  // Locale
  locale: "en",
  setLocale: (locale) => set({ locale }),

  // Video call
  isInCall: false,
  setInCall: (inCall) => set({ isInCall: inCall }),
  callParticipantCount: 0,
  setCallParticipantCount: (count) => set({ callParticipantCount: count }),

  // Waiting room
  waitingPatientCount: 0,
  setWaitingPatientCount: (count) => set({ waitingPatientCount: count }),
}));
