import { describe, expect, it } from 'vitest';
import { moneyInWords } from './moneyWords';

describe('moneyInWords', () => {
  it('đọc số tiền theo mẫu phiếu nhập kho', () => {
    expect(moneyInWords(65946000)).toBe('Sáu mươi lăm triệu chín trăm bốn mươi sáu nghìn đồng chẵn.');
  });

  it('đọc mười, mốt, lăm, linh, không trăm', () => {
    expect(moneyInWords(15)).toBe('Mười lăm đồng chẵn.');
    expect(moneyInWords(21)).toBe('Hai mươi mốt đồng chẵn.');
    expect(moneyInWords(105)).toBe('Một trăm linh năm đồng chẵn.');
    expect(moneyInWords(1005)).toBe('Một nghìn không trăm linh năm đồng chẵn.');
    expect(moneyInWords(1000050)).toBe('Một triệu không trăm năm mươi đồng chẵn.');
  });

  it('đọc tỷ, số 0, làm tròn đến đồng', () => {
    expect(moneyInWords(2000000000)).toBe('Hai tỷ đồng chẵn.');
    expect(moneyInWords(0)).toBe('Không đồng.');
    expect(moneyInWords(999.6)).toBe('Một nghìn đồng chẵn.');
  });
});
