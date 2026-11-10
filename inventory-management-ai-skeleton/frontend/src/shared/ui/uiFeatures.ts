import { createContext, useContext } from 'react';

/**
 * Cấu hình ẩn / đổi tên menu và nút theo khách hàng (D-72), sửa bằng SQL ở bảng sys_ui_features. Ẩn chỉ phục vụ giao
 * diện; server vẫn kiểm tra quyền. Khóa không có trong cấu hình: bật, giữ tên mặc định.
 */
export interface UiFeatureSetting {
  key: string;
  enabled: boolean;
  customLabel: string | null;
}

/** Nút được phép ẩn; khóa trong DB là `action:<id>` (migration V43). */
export type UiActionId =
  'ai-assistant' | 'purchase-order.ocr' | 'sales-order.ocr' | 'excel-import' | 'excel-export';

export interface UiFeatures {
  isEnabled(key: string): boolean;
  label(key: string, fallback: string): string;
}

export const ALL_ENABLED: UiFeatures = {
  isEnabled: () => true,
  label: (_key, fallback) => fallback,
};

export function toUiFeatures(settings: UiFeatureSetting[]): UiFeatures {
  const byKey = new Map(settings.map((setting) => [setting.key, setting]));
  return {
    isEnabled: (key) => byKey.get(key)?.enabled ?? true,
    label: (key, fallback) => byKey.get(key)?.customLabel?.trim() || fallback,
  };
}

export const UiFeaturesContext = createContext<UiFeatures>(ALL_ENABLED);

export function useUiFeatures(): UiFeatures {
  return useContext(UiFeaturesContext);
}

export function useUiAction(id: UiActionId): boolean {
  return useUiFeatures().isEnabled(`action:${id}`);
}
