import React, { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { StyleSheet } from 'react-native';
import WebView, { WebViewMessageEvent } from 'react-native-webview';

import { preparePdfJsRuntime, stagePdfForExtraction } from '../lib/pdfAssets';

export interface PdfTextExtractorHandle {
  extractText(
    pdfUri: string,
    onProgress?: (page: number, totalPages: number) => void
  ): Promise<string[]>;
}

type PendingJob = {
  resolve: (pages: string[]) => void;
  reject: (error: Error) => void;
  onProgress?: (page: number, totalPages: number) => void;
};

type ExtractorMessage =
  | { type: 'progress'; page: number; totalPages: number }
  | { type: 'done'; pages: string[]; totalPages: number }
  | { type: 'error'; message: string };

/** Headless, always-mounted WebView that runs pdf.js to turn a PDF into plain text pages. */
const PdfTextExtractor = forwardRef<PdfTextExtractorHandle>((_props, ref) => {
  const webViewRef = useRef<WebView>(null);
  const pendingJob = useRef<PendingJob | null>(null);
  const [runtimeUri, setRuntimeUri] = useState<string | null>(null);

  useImperativeHandle(ref, () => ({
    async extractText(pdfUri, onProgress) {
      if (pendingJob.current) {
        throw new Error('Ya hay una extracción de PDF en curso.');
      }

      const { fileName } = await stagePdfForExtraction(pdfUri);

      if (!runtimeUri) {
        const dir = await preparePdfJsRuntime();
        setRuntimeUri(`${dir.uri}extract.html`);
        // Wait a tick for the WebView to (re)mount with the new source before messaging it.
        await new Promise((resolve) => setTimeout(resolve, 300));
      }

      return new Promise<string[]>((resolve, reject) => {
        pendingJob.current = { resolve, reject, onProgress };
        webViewRef.current?.postMessage(JSON.stringify({ type: 'extract', pdfUri: fileName }));
      });
    },
  }));

  function handleMessage(event: WebViewMessageEvent) {
    const job = pendingJob.current;
    if (!job) return;

    let data: ExtractorMessage;
    try {
      data = JSON.parse(event.nativeEvent.data);
    } catch {
      return;
    }

    if (data.type === 'progress') {
      job.onProgress?.(data.page, data.totalPages);
    } else if (data.type === 'done') {
      pendingJob.current = null;
      job.resolve(data.pages);
    } else if (data.type === 'error') {
      pendingJob.current = null;
      job.reject(new Error(data.message));
    }
  }

  if (!runtimeUri) {
    // Prepare the runtime eagerly so the first real extraction call is fast.
    preparePdfJsRuntime()
      .then((dir) => setRuntimeUri(`${dir.uri}extract.html`))
      .catch(() => {});
    return null;
  }

  return (
    <WebView
      ref={webViewRef}
      source={{ uri: runtimeUri }}
      onMessage={handleMessage}
      originWhitelist={['*']}
      allowFileAccess
      allowUniversalAccessFromFileURLs
      allowFileAccessFromFileURLs
      javaScriptEnabled
      domStorageEnabled
      style={styles.hidden}
    />
  );
});

PdfTextExtractor.displayName = 'PdfTextExtractor';

const styles = StyleSheet.create({
  // WebView can't be display:none (it stops running JS on Android), so it's
  // shrunk to a 1x1 offscreen view instead.
  hidden: {
    position: 'absolute',
    top: -1000,
    left: 0,
    width: 1,
    height: 1,
  },
});

export default PdfTextExtractor;
