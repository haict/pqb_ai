import { createContext, useContext } from 'react';
import type { UiFeatures } from './uiFeatures';

/**
 * Chế độ hạch toán của bản cài đặt (D-73), sửa bằng SQL ở sys_app_settings.posting.mode. INVENTORY_ONLY: ghi sổ đơn
 * mua / đơn bán chỉ ghi kho; giao diện ẩn công nợ, kế toán, thu chi và báo cáo theo tiền.
 */
export type PostingMode = 'FULL' | 'INVENTORY_ONLY';

/** Menu chỉ có ý nghĩa khi có công nợ / kế toán; ẩn ở INVENTORY_ONLY (khóa giống sys_ui_features). */
export const FINANCIAL_FEATURE_KEYS: readonly string[] = [
  'group:finance',
  'group:accounting',
  'menu:/purchasing/cost-allocations',
  'menu:/purchasing/ledger',
  'menu:/sales/report',
  'menu:/sales/ledger',
  'menu:/reports/purchase-analysis',
  'menu:/reports/cash-flow-forecast',
];

/** Gộp chế độ hạch toán vào cấu hình ẩn / hiện: INVENTORY_ONLY tắt thêm các menu tài chính. */
export function withPostingMode(features: UiFeatures, mode: PostingMode): UiFeatures {
  if (mode === 'FULL') {
    return features;
  }
  const hidden = new Set(FINANCIAL_FEATURE_KEYS);
  return {
    isEnabled: (key) => !hidden.has(key) && features.isEnabled(key),
    label: features.label,
  };
}

export const PostingModeContext = createContext<PostingMode>('FULL');

/** true khi bản cài đặt có công nợ, kế toán (chế độ FULL). */
export function useFinancialPosting(): boolean {
  return useContext(PostingModeContext) === 'FULL';
}
