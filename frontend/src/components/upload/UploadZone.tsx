import { DropArea } from './DropArea';
import { ImagePreview } from './ImagePreview';
import { FocusHintInput } from './FocusHintInput';
import { AnalyzeButton } from './AnalyzeButton';
import { useStore } from '../../store';

export function UploadZone() {
  const { previewUrl } = useStore();
  return (
    <section aria-label="Upload screenshot for analysis" className="w-full max-w-xl mx-auto">
      <DropArea />
      <ImagePreview previewUrl={previewUrl} />
      <FocusHintInput />
      <AnalyzeButton />
    </section>
  );
}
