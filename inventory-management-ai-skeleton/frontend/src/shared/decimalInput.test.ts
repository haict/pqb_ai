import { describe, expect, it } from 'vitest';
import { formatDecimalInput, parseDecimalInput } from './decimalInput';

describe('decimalInput', () => {
  it('không thêm số 0 thừa và dùng định dạng Việt Nam', () => {
    expect(formatDecimalInput(1, 3)).toBe('1');
    expect(formatDecimalInput('1.5', 3)).toBe('1,5');
    expect(formatDecimalInput(1234.5678, 3)).toBe('1.234,568');
    expect(formatDecimalInput(undefined, 3)).toBe('');
  });

  it('đọc dấu phẩy là thập phân, dấu chấm là hàng nghìn', () => {
    expect(parseDecimalInput('1,5', 3)).toBe('1.5');
    expect(parseDecimalInput('1.000', 3)).toBe('1000');
    expect(parseDecimalInput('2,12345', 3)).toBe('2.123');
    expect(parseDecimalInput('', 3)).toBe('');
  });
});
