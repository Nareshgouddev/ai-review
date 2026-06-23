/**
 * Axios API client for the AI UI Review Agent frontend.
 * Configures base URL, session ID injection, and typed endpoint functions.
 */

import axios from 'axios';
import type { AnalysisResultDto } from './types';

// ---- DTO re-exported for consumers -----------------------------------------

export interface AnalysisSummaryDto {
  id: string;
  sessionId: string;
  createdAt: string; // ISO-8601
  overallScore: number;
  processingMs: number;
  imageHash: string | null;
}

/** Shape of error responses from the backend GlobalExceptionHandler */
export interface ErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
}

/** Union of all known backend error codes */
export type ErrorCode =
  | 'INVALID_FILE_TYPE'
  | 'FILE_TOO_LARGE'
  | 'ANALYSIS_FAILED'
  | 'RATE_LIMIT_EXCEEDED'
  | 'UPSTREAM_API_ERROR'
  | 'SERVICE_OVERLOADED';

// ---- Session ID ------------------------------------------------------------

/**
 * Returns a stable session ID for the current browser session.
 * Generated once via crypto.randomUUID() and persisted in sessionStorage.
 */
function getSessionId(): string {
  const STORAGE_KEY = 'sessionId';
  let id = sessionStorage.getItem(STORAGE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    sessionStorage.setItem(STORAGE_KEY, id);
  }
  return id;
}

// ---- Axios instance --------------------------------------------------------

const apiClient = axios.create({
  baseURL: '/api/v1',
});

// Request interceptor: attach X-Session-Id header on every request
apiClient.interceptors.request.use((config) => {
  config.headers['X-Session-Id'] = getSessionId();
  return config;
});

// Response interceptor: reject with error as-is so the store can handle it
apiClient.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error),
);

// ---- Typed API functions ---------------------------------------------------

/**
 * Submits an image for AI analysis.
 *
 * @param file      The image file to analyze.
 * @param focusHint Optional text directing the AI toward specific concerns.
 * @returns         The structured analysis result from the backend.
 */
export async function analyzeImage(
  file: File,
  focusHint?: string,
): Promise<AnalysisResultDto> {
  const formData = new FormData();
  formData.append('file', file);
  if (focusHint && focusHint.trim().length > 0) {
    formData.append('focusHint', focusHint.trim());
  }

  const response = await apiClient.post<AnalysisResultDto>('/analyze', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return response.data;
}

/**
 * Retrieves recent analysis history for the current session.
 *
 * @param limit Maximum number of entries to return (default: 5).
 * @returns     Array of analysis summaries, newest first.
 */
export async function getHistory(limit = 5): Promise<AnalysisSummaryDto[]> {
  const response = await apiClient.get<AnalysisSummaryDto[]>('/history', {
    params: {
      sessionId: getSessionId(),
      limit,
    },
  });
  return response.data;
}

export default apiClient;
