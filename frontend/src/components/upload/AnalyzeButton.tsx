import { Loader2 } from 'lucide-react';
import { useStore } from '../../store';

export function AnalyzeButton() {
  const { selectedFile, isAnalyzing, analysisError, submitAnalysis } = useStore();
  const isDisabled = !selectedFile || isAnalyzing;

  return (
    <div className="mt-5">
      <button
        type="button"
        disabled={isDisabled}
        onClick={() => submitAnalysis()}
        aria-busy={isAnalyzing}
        className={`
          w-full flex items-center justify-center gap-2 rounded-xl px-6 py-3
          font-semibold text-sm transition-all duration-200
          ${isDisabled
            ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
            : 'bg-indigo-600 text-white hover:bg-indigo-700 active:scale-[0.98] shadow-sm'}
        `}
      >
        {isAnalyzing && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
        {isAnalyzing ? 'Analyzing…' : 'Analyze UI'}
      </button>
      {analysisError && (
        <p role="alert" className="mt-2 text-sm text-red-600 text-center">{analysisError}</p>
      )}
    </div>
  );
}
