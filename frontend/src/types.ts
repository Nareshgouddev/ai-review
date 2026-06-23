/**
 * TypeScript interfaces for the AI UI Review Agent frontend.
 * Mirrors the backend DTO definitions from design.md.
 */

export interface AnalysisResultDto {
  /** UUID v4 identifier for this analysis record */
  id: string;
  /** Browser-scoped session identifier */
  sessionId: string;
  /** Aggregate UI quality score (0–100) */
  overallScore: number;
  /** Time taken to process the analysis in milliseconds */
  processingMs: number;
  /** Whether this result was served from the 24-hour cache */
  cached: boolean;
  /** Breakdown across the five evaluation categories */
  categories: CategoryDto[];
}

export interface CategoryDto {
  /** Category name: Layout | Typography | Color & Contrast | Accessibility | Consistency */
  name: string;
  /** Category score (0–100) */
  score: number;
  /** Score weight used in overall computation (0.0–1.0) */
  weight: number;
  /** List of actionable suggestions for this category */
  suggestions: SuggestionDto[];
}

export interface SuggestionDto {
  /** Severity level of the suggestion */
  severity: 'Critical' | 'Warning' | 'Suggestion';
  /** Short, descriptive title */
  title: string;
  /** Detailed description of the issue */
  description: string;
  /** Specific, actionable recommendation */
  recommendation: string;
}

export interface HistoryEntry {
  /** UUID matching the AnalysisResultDto id */
  id: string;
  /** Base64 data URL of a 128×128 pixel thumbnail */
  thumbnailDataUrl: string;
  /** ISO-8601 timestamp of when the analysis was performed */
  timestamp: string;
  /** Overall score from the analysis (0–100) */
  overallScore: number;
  /** Processing duration in milliseconds */
  processingMs: number;
  /** Full analysis result for restoring the Analysis Panel */
  result: AnalysisResultDto;
}
