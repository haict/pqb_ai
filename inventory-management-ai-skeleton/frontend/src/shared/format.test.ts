import { describe, expect, it } from 'vitest';
import { formatAmount, formatQuantity } from './format';

describe('định dạng số D-57', () => {
  it('số lượng luôn 2 số lẻ, số âm trong ngoặc', () => {
    expect(formatQuantity(114300)).toBe('114.300,00');
    expect(formatQuantity(0)).toBe('0,00');
    expect(formatQuantity(-5955)).toBe('(5.955,00)');
    expect(formatQuantity(null)).toBe('');
  });

  it('giá trị làm tròn đến đồng, số âm trong ngoặc', () => {
    expect(formatAmount(373400000)).toBe('373.400.000');
    expect(formatAmount(0)).toBe('0');
    expect(formatAmount(-950000)).toBe('(950.000)');
    expect(formatAmount(undefined)).toBe('');
  });
});
