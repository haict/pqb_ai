import type { ReactNode } from 'react';
import { useUiAction, type UiActionId } from './uiFeatures';

/** Hiện nút khi chức năng không bị ẩn theo cấu hình khách hàng (D-72); không thay kiểm tra quyền. */
export function UiAction({ id, children }: { id: UiActionId; children: ReactNode }) {
  return useUiAction(id) ? <>{children}</> : null;
}
