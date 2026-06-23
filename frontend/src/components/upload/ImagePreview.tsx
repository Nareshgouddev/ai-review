interface ImagePreviewProps { previewUrl: string | null; }

export function ImagePreview({ previewUrl }: ImagePreviewProps) {
  if (!previewUrl) return null;
  return (
    <div className="mt-4 flex justify-center">
      <img
        src={previewUrl}
        alt="Selected screenshot preview"
        className="max-h-48 rounded-lg border border-gray-200 shadow-sm object-contain"
      />
    </div>
  );
}
