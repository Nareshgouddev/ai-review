import React, { useRef, useState } from 'react';
import { Upload } from 'lucide-react';
import { useStore } from '../../store';

export function DropArea() {
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { setFile, analysisError } = useStore();

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation();
    setIsDragOver(true);
  };
  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation();
    setIsDragOver(false);
  };
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation();
    setIsDragOver(true);
  };
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation();
    setIsDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) setFile(file);
  };
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) setFile(file);
    e.target.value = '';
  };

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Upload zone: drag and drop an image or click to browse"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
      onClick={() => fileInputRef.current?.click()}
      onKeyDown={(e) => e.key === 'Enter' && fileInputRef.current?.click()}
      className={`
        relative border-2 border-dashed rounded-xl p-10 text-center cursor-pointer
        transition-all duration-200 select-none
        ${isDragOver
          ? 'border-indigo-500 bg-indigo-50 scale-[1.01]'
          : 'border-gray-300 bg-gray-50 hover:border-indigo-400 hover:bg-indigo-50/50'}
      `}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept=".png,.jpg,.jpeg,.webp"
        className="sr-only"
        aria-label="File picker"
        onChange={handleFileChange}
        tabIndex={-1}
      />
      <Upload className="mx-auto mb-3 h-10 w-10 text-indigo-400" aria-hidden="true" />
      <p className="text-sm font-medium text-gray-700">
        Drag &amp; drop a screenshot here, or <span className="text-indigo-600 underline">browse</span>
      </p>
      <p className="mt-1 text-xs text-gray-500">PNG, JPG, WebP · max 10 MB</p>

      {analysisError && (
        <p role="alert" className="mt-3 text-sm text-red-600 font-medium">{analysisError}</p>
      )}
    </div>
  );
}
