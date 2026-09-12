import React, { createContext, useContext, useRef } from 'react';

import PdfTextExtractor, { PdfTextExtractorHandle } from '../components/PdfTextExtractor';

const PdfExtractorContext = createContext<React.RefObject<PdfTextExtractorHandle | null> | null>(null);

export function PdfExtractorProvider({ children }: { children: React.ReactNode }) {
  const ref = useRef<PdfTextExtractorHandle | null>(null);
  return (
    <PdfExtractorContext.Provider value={ref}>
      {children}
      <PdfTextExtractor ref={ref} />
    </PdfExtractorContext.Provider>
  );
}

export function usePdfExtractor(): PdfTextExtractorHandle {
  const ref = useContext(PdfExtractorContext);
  if (!ref) throw new Error('usePdfExtractor debe usarse dentro de PdfExtractorProvider');
  return {
    async extractText(pdfUri, onProgress) {
      if (!ref.current) throw new Error('El extractor de PDF todavía no está listo, intenta de nuevo.');
      return ref.current.extractText(pdfUri, onProgress);
    },
  };
}
