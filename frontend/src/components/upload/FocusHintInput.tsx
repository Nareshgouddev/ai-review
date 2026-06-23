import { useStore } from '../../store';

export function FocusHintInput() {
  const { focusHint, setFocusHint } = useStore();
  return (
    <div className="mt-4">
      <label htmlFor="focus-hint" className="block text-sm font-medium text-gray-700 mb-1">
        Focus area <span className="text-gray-400 font-normal">(optional)</span>
      </label>
      <input
        id="focus-hint"
        type="text"
        maxLength={256}
        value={focusHint}
        onChange={(e) => setFocusHint(e.target.value)}
        placeholder="e.g. focus on mobile layout, check color contrast"
        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm
                   placeholder:text-gray-400 focus:outline-none focus:ring-2
                   focus:ring-indigo-500 focus:border-indigo-500"
        aria-describedby="focus-hint-desc"
      />
      <p id="focus-hint-desc" className="mt-1 text-xs text-gray-500">
        Direct the AI to focus on specific aspects of your UI
      </p>
    </div>
  );
}
