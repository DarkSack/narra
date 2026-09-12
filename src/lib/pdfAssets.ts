import { Asset } from 'expo-asset';
import { Directory, File, Paths } from 'expo-file-system';

import extractHtmlModule from '../../assets/pdfjs/extract.html';
import pdfLibModule from '../../assets/pdfjs/pdf.min.js.pdfjslib';
import pdfWorkerModule from '../../assets/pdfjs/pdf.worker.min.js.pdfjslib';

const PDFJS_DIR_NAME = 'pdfjs';

let preparedDirPromise: Promise<Directory> | null = null;

async function copyAsset(moduleId: number, destination: File): Promise<void> {
  const asset = Asset.fromModule(moduleId);
  await asset.downloadAsync();
  if (!asset.localUri) {
    throw new Error(`No se pudo preparar el asset requerido para leer PDFs.`);
  }
  const source = new File(asset.localUri);
  if (destination.exists) destination.delete();
  await source.copy(destination);
}

/** Copies the pdf.js runtime (html shell + library + worker) into a writable,
 * same-directory location so the WebView can load them together. Runs once. */
export function preparePdfJsRuntime(): Promise<Directory> {
  if (!preparedDirPromise) {
    preparedDirPromise = (async () => {
      const dir = new Directory(Paths.document, PDFJS_DIR_NAME);
      if (!dir.exists) dir.create({ intermediates: true });

      await copyAsset(extractHtmlModule, new File(dir, 'extract.html'));
      await copyAsset(pdfLibModule, new File(dir, 'pdf.min.js'));
      await copyAsset(pdfWorkerModule, new File(dir, 'pdf.worker.min.js'));

      return dir;
    })();
  }
  return preparedDirPromise;
}

/** Copies the picked PDF next to the pdf.js runtime so it can be fetched
 * same-origin/same-directory from the WebView, and returns its file name. */
export async function stagePdfForExtraction(pdfUri: string): Promise<{ dir: Directory; fileName: string }> {
  const dir = await preparePdfJsRuntime();
  const staged = new File(dir, 'current.pdf');
  if (staged.exists) staged.delete();
  const source = new File(pdfUri);
  await source.copy(staged);
  return { dir, fileName: staged.name };
}
