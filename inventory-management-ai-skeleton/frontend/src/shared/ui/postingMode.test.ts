import { withPostingMode } from './postingMode';
import { toUiFeatures } from './uiFeatures';

describe('withPostingMode (D-73)', () => {
  const features = toUiFeatures([{ key: 'menu:/inventory/recalls', enabled: false, customLabel: null }]);

  it('FULL giữ nguyên cấu hình', () => {
    const merged = withPostingMode(features, 'FULL');
    expect(merged.isEnabled('group:finance')).toBe(true);
    expect(merged.isEnabled('menu:/inventory/recalls')).toBe(false);
  });

  it('INVENTORY_ONLY ẩn thêm công nợ, kế toán, báo cáo theo tiền; giữ cấu hình D-72', () => {
    const merged = withPostingMode(features, 'INVENTORY_ONLY');
    for (const key of [
      'group:finance',
      'group:accounting',
      'menu:/sales/report',
      'menu:/purchasing/cost-allocations',
    ]) {
      expect(merged.isEnabled(key)).toBe(false);
    }
    expect(merged.isEnabled('menu:/sales/orders')).toBe(true);
    expect(merged.isEnabled('menu:/inventory/recalls')).toBe(false);
  });
});
