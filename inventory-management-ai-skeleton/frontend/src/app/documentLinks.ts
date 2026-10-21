/**
 * Đường dẫn trang chi tiết theo loại chứng từ, để mở chứng từ từ thông báo.
 * Phân hệ nào có trang chi tiết chứng từ thì đăng ký ở đây.
 */
const DOCUMENT_ROUTES: Record<string, (id: number) => string> = {};

export function registerDocumentRoute(documentType: string, route: (id: number) => string) {
  DOCUMENT_ROUTES[documentType] = route;
}

export function documentLink(documentType: string | null, id: number | null): string | null {
  if (!documentType || id === null) {
    return null;
  }
  const route = DOCUMENT_ROUTES[documentType];
  return route ? route(id) : null;
}
