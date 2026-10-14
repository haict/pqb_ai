import type { TDocumentDefinitions } from 'pdfmake/interfaces';

interface PdfMake {
  addVirtualFileSystem(vfs: unknown): void;
  createPdf(definition: TDocumentDefinitions): { getBlob(): Promise<Blob> };
}

/** pdfmake bản trình duyệt là UMD: lấy {@code default} nếu bundler bọc lại, ngược lại dùng chính module. */
function unwrap<T>(module: T | { default: T }): T {
  return typeof module === 'object' && module !== null && 'default' in module ? module.default : module;
}

/** Dựng file PDF từ định nghĩa pdfmake; thư viện và font chỉ tải khi người dùng bấm tải (khá nặng). */
export async function pdfBlob(definition: TDocumentDefinitions): Promise<Blob> {
  const [pdfMakeModule, fontsModule] = await Promise.all([
    import('pdfmake/build/pdfmake'),
    import('pdfmake/build/vfs_fonts'),
  ]);
  const pdfMake = unwrap(pdfMakeModule as unknown as PdfMake | { default: PdfMake });
  pdfMake.addVirtualFileSystem(unwrap(fontsModule as unknown));
  return pdfMake.createPdf(definition).getBlob();
}
