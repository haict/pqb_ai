import { registerDocumentRoute } from '../../app/documentLinks';

/** Mở chứng từ kho từ thông báo (mã loại chứng từ = sys_document_types.code). */
export function registerInventoryDocumentRoutes() {
  for (const type of ['PNK', 'PXK', 'PXH', 'PCK', 'PDC', 'PCL', 'PDK']) {
    registerDocumentRoute(type, (id) => `/inventory/documents/${id}`);
  }
  registerDocumentRoute('PKK', (id) => `/inventory/stock-counts?id=${id}`);
  registerDocumentRoute('PTH', (id) => `/inventory/recalls?id=${id}`);
  registerDocumentRoute('CSX', (id) => `/inventory/stock-issue-policies?id=${id}`);
}
