import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import type { CatalogMatch, OcrLine, OcrValue, SalesOcrResult } from '../../ai/types';
import { salesOcrFill } from './salesOcrFill';

const value = (text: string | null, status: OcrValue['status'] = 'OK'): OcrValue => ({
  value: text,
  rawText: text,
  source: text === null ? 'UNKNOWN' : 'OCR',
  confidence: text === null ? null : 0.98,
  status: text === null ? 'UNKNOWN' : status,
  warnings: [],
});
const none = value(null);
const match = (partial: Partial<CatalogMatch>): CatalogMatch => ({
  status: 'MATCHED',
  matchedBy: 'CODE',
  reviewRequired: false,
  id: null,
  code: null,
  name: null,
  rawText: null,
  candidates: [],
  ...partial,
});
const unknown = match({ status: 'UNKNOWN', matchedBy: null });
const noCheck = { status: 'UNKNOWN' as const, calculated: null, printed: null, warnings: [] };

const line = (partial: Partial<OcrLine>): OcrLine => ({
  product: unknown,
  unit: unknown,
  taxRate: unknown,
  quantity: none,
  unitPrice: none,
  discountPercent: none,
  printedAmount: none,
  amountCheck: noCheck,
  ...partial,
});

function result(partial: Partial<SalesOcrResult['header']> = {}, lines: OcrLine[] = []): SalesOcrResult {
  return {
    documentType: 'SALES_ORDER',
    pageCount: 1,
    header: {
      customer: match({ matchedBy: 'TAX_CODE', id: 5, code: 'KH_PK01', name: 'Phòng khám' }),
      documentDate: value('2026-10-18'),
      description: value('Theo đơn đặt hàng của khách số PDH-01'),
      deliveryAddress: value('S 1 ph mu', 'LOW_CONFIDENCE'),
      currencyCode: value('VND'),
      ...partial,
    },
    lines,
    totals: { goods: none, vat: none, total: none, check: noCheck },
    warnings: [],
  };
}

describe('salesOcrFill (AI-2)', () => {
  it('điền khách theo MST, diễn giải; địa chỉ giao chỉ ghi chú, không tự điền', () => {
    const fill = salesOcrFill({}, result());
    expect(fill.header.customerId).toBe(5);
    expect(fill.header.customerLabel).toBe('KH_PK01 - Phòng khám');
    expect(fill.header.description).toBe('Theo đơn đặt hàng của khách số PDH-01');
    expect(fill.header.documentDate?.format('YYYY-MM-DD')).toBe('2026-10-18');
    expect(fill.header.deliveryAddress).toBeUndefined();
    expect(fill.notes).toEqual([expect.stringContaining('S 1 ph mu')]);
  });

  it('không ghi đè khách, ngày đã chọn; báo khác biệt', () => {
    const fill = salesOcrFill({ customerId: 9, documentDate: dayjs('2026-10-20') }, result());
    expect(fill.header.customerId).toBeUndefined();
    expect(fill.header.documentDate).toBeUndefined();
    expect(fill.notes).toEqual(
      expect.arrayContaining([
        expect.stringContaining('khác khách hàng đang chọn'),
        expect.stringContaining('18/10/2026'),
      ]),
    );
  });

  it('khách không có trong danh mục: không gán, ghi chú; dòng hàng kèm điều cần kiểm tra', () => {
    const fill = salesOcrFill(
      {},
      result({ customer: match({ status: 'NOT_FOUND', matchedBy: null, rawText: 'BV X (0100000009)' }) }, [
        line({
          product: match({ id: 3, code: 'TT_GLU_01', name: 'Glucose' }),
          quantity: value('10'),
          unitPrice: value('100000', 'LOW_CONFIDENCE'),
        }),
      ]),
    );
    expect(fill.header.customerId).toBeUndefined();
    expect(fill.notes[0]).toContain('Không tìm thấy khách hàng BV X (0100000009)');
    expect(fill.lines[0]).toMatchObject({ productId: 3, quantity: 10, unitPrice: 100000 });
    expect(fill.lines[0]?.ocrHints).toEqual(['Đơn giá đọc chưa chắc chắn: kiểm tra lại.']);
  });
});
