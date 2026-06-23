/**
 * Zustand store for the AI UI Review Agent.
 * Manages upload state, analysis state, and session-scoped history.
 */

import { create } from 'zustand';
import type { AnalysisResultDto, HistoryEntry } from '../types';
import { analyzeImage } from '../api';
import type { ErrorCode } from '../api';

// ---- Constants -------------------------------------------------------------

/** MIME types accepted by the backend — mirrors backend validation */
const ALLOWED_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/jpg',
]);

/** Maximum file size in bytes (exclusive upper bound — 10 MB) */
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

/** Maximum number of history entries retained in state and sessionStorage */
const MAX_HISTORY = 5;

/** sessionStorage key for persisted history */
const HISTORY_STORAGE_KEY = 'uireview_history';

// ---- Error code → human-readable messages ----------------------------------

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_FILE_TYPE: 'Invalid file type. Please upload a PNG, JPG, or WebP image.',
  FILE_TOO_LARGE: 'File is too large. Maximum size is 10 MB.',
  ANALYSIS_FAILED: 'AI analysis failed. The image could not be processed.',
  RATE_LIMIT_EXCEEDED: 'Too many requests. Please wait a moment and try again.',
  UPSTREAM_API_ERROR: 'AI service is unavailable. Please try again later.',
  SERVICE_OVERLOADED: 'Service is currently overloaded. Please try again shortly.',
};

/** Maps a backend errorCode to a user-friendly message, with a fallback. */
function toHumanReadable(errorCode?: string): string {
  if (errorCode && errorCode in ERROR_MESSAGES) {
    return ERROR_MESSAGES[errorCode as ErrorCode];
  }
  return 'An unexpected error occurred. Please try again.';
}

// ---- Thumbnail helper ------------------------------------------------------

/**
 * Renders a 128×128 JPEG thumbnail of the given file using an offscreen canvas.
 * Resolves to a data URL string.
 */
function generateThumbnail(file: File): Promise<string> {
  return new Promise((resolve) => {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext('2d');
    const img = new Image();
    img.onload = () => {
      ctx?.drawImage(img, 0, 0, 128, 128);
      resolve(canvas.toDataURL('image/jpeg', 0.7));
    };
    img.src = URL.createObjectURL(file);
  });
}

// ---- State interface -------------------------------------------------------

interface AppState {
  // Upload state
  selectedFile: File | null;
  previewUrl: string | null;
  focusHint: string;

  // Analysis state
  analysisResult: AnalysisResultDto | null;
  isAnalyzing: boolean;
  analysisError: string | null;

  // History (mirrored in sessionStorage)
  history: HistoryEntry[];

  // Actions
  setFile: (file: File | null) => void;
  setFocusHint: (hint: string) => void;
  submitAnalysis: () => Promise<void>;
  restoreFromHistory: (entry: HistoryEntry) => void;
  clearHistory: () => void;
  loadHistoryFromStorage: () => void;
}

// ---- Store implementation --------------------------------------------------

export const useStore = create<AppState>((set, get) => ({
  // ---- Initial state -------------------------------------------------------

  selectedFile: null,
  previewUrl: null,
  focusHint: '',

  analysisResult: null,
  isAnalyzing: false,
  analysisError: null,

  history: [],

  // ---- Actions ------------------------------------------------------------

  /**
   * Validates and stores the selected file.
   * On validation failure, sets analysisError and clears selectedFile.
   * On success, reads the file with FileReader to produce a data URL preview.
   */
  setFile: (file: File | null) => {
    if (file === null) {
      set({ selectedFile: null, previewUrl: null, analysisError: null });
      return;
    }

    // Client-side MIME type validation
    if (!ALLOWED_TYPES.has(file.type)) {
      set({
        selectedFile: null,
        previewUrl: null,
        analysisError: ERROR_MESSAGES.INVALID_FILE_TYPE,
      });
      return;
    }

    // Client-side size validation (mirrors backend: reject >= 10 MB)
    if (file.size >= MAX_FILE_SIZE) {
      set({
        selectedFile: null,
        previewUrl: null,
        analysisError: ERROR_MESSAGES.FILE_TOO_LARGE,
      });
      return;
    }

    // Valid file — generate inline preview via FileReader
    const reader = new FileReader();
    reader.onload = (e) => {
      set({ previewUrl: e.target?.result as string });
    };
    reader.readAsDataURL(file);

    set({ selectedFile: file, analysisError: null });
  },

  /** Updates the optional focus hint text. */
  setFocusHint: (hint: string) => {
    set({ focusHint: hint });
  },

  /**
   * Calls the API, manages loading state, and updates history on success.
   * Maps backend error codes to human-readable messages on failure.
   */
  submitAnalysis: async () => {
    const { selectedFile, focusHint, history } = get();

    if (!selectedFile) {
      set({ analysisError: 'Please select a file before analyzing.' });
      return;
    }

    set({ isAnalyzing: true, analysisError: null });

    try {
      const result = await analyzeImage(selectedFile, focusHint || undefined);

      // Generate thumbnail for the history entry
      const thumbnailDataUrl = await generateThumbnail(selectedFile);

      const newEntry: HistoryEntry = {
        id: result.id,
        thumbnailDataUrl,
        timestamp: new Date().toISOString(),
        overallScore: result.overallScore,
        processingMs: result.processingMs,
        result,
      };

      // Prepend newest entry and cap at MAX_HISTORY
      const updatedHistory: HistoryEntry[] = [newEntry, ...history].slice(
        0,
        MAX_HISTORY,
      );

      // Persist to sessionStorage
      try {
        sessionStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(updatedHistory));
      } catch {
        // sessionStorage write failures are non-fatal
      }

      set({
        analysisResult: result,
        isAnalyzing: false,
        history: updatedHistory,
      });
    } catch (error: unknown) {
      // Extract backend errorCode from Axios error response
      let errorCode: string | undefined;

      if (
        error !== null &&
        typeof error === 'object' &&
        'response' in error &&
        (error as { response?: { data?: { errorCode?: string } } }).response?.data
          ?.errorCode
      ) {
        errorCode = (
          error as { response: { data: { errorCode: string } } }
        ).response.data.errorCode;
      }

      set({
        analysisError: toHumanReadable(errorCode),
        isAnalyzing: false,
      });
    }
  },

  /**
   * Restores an analysis result from a history entry into the Analysis Panel.
   */
  restoreFromHistory: (entry: HistoryEntry) => {
    set({ analysisResult: entry.result });
  },

  /**
   * Clears all history entries from both React state and sessionStorage.
   */
  clearHistory: () => {
    sessionStorage.removeItem(HISTORY_STORAGE_KEY);
    set({ history: [] });
  },

  /**
   * Reads history from sessionStorage and hydrates the store.
   * Should be called on application mount (e.g., in HistoryPanel useEffect).
   */
  loadHistoryFromStorage: () => {
    try {
      const raw = sessionStorage.getItem(HISTORY_STORAGE_KEY);
      if (raw) {
        const parsed: HistoryEntry[] = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          set({ history: parsed.slice(0, MAX_HISTORY) });
        }
      }
    } catch {
      // Corrupted storage — silently ignore
    }
  },
}));
