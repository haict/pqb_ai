import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import type { CatalogMatch, OcrValue, OcrLine, PurchaseOcrResult } from '../../ai/types';
import { purchaseOcrFill, type OcrLineValues } from './purchaseOcrFill';

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

function line(partial: Partial<OcrLine>): OcrLine {
  return {
    product: unknown,
    unit: unknown,
    taxRate: unknown,
    quantity: none,
    unitPrice: none,
    discountPercent: none,
    printedAmount: none,
    amountCheck: { status: 'UNKNOWN', calculated: null, printed: null, warnings: [] },
    ...partial,
  };
}

function result(partial: Partial<PurchaseOcrResult> = {}): PurchaseOcrResult {
  return {
    documentType: 'PURCHASE_INVOICE',
    pageCount: 1,
    header: {
      supplier: match({ matchedBy: 'TAX_CODE', id: 7, code: 'NCC_01', name: 'Nhà phân phối' }),
      invoiceSeries: value('1C26TAA'),
      invoiceNo: value('0000131'),
      invoiceDate: value('2026-10-18'),
      documentDate: none,
      description: none,
      currencyCode: value('VND'),
    },
    lines: [],
    totals: {
      goods: none,
      vat: none,
      total: none,
      check: { status: 'UNKNOWN', calculated: null, printed: null, warnings: [] },
    },
    warnings: [],
    ...partial,
  };
}

describe('purchaseOcrFill (D-64)', () => {
  it('điền ô đầu phiếu còn trống: nhà cung cấp theo MST, ký hiệu, số, ngày hóa đơn', () => {
    const fill = purchaseOcrFill({}, result());
    expect(fill.header.supplierId).toBe(7);
    expect(fill.header.supplierLabel).toBe('NCC_01 - Nhà phân phối');
    expect(fill.header.invoiceSeries).toBe('1C26TAA');
    expect(fill.header.invoiceNo).toBe('0000131');
    expect(fill.header.invoiceDate?.format('YYYY-MM-DD')).toBe('2026-10-18');
    expect(fill.notes).toEqual([]);
  });

  it('không ghi đè ô người dùng đã nhập; báo giá trị trên chứng từ khác', () => {
    const fill = purchaseOcrFill(
      { supplierId: 9, invoiceNo: '0000999', invoiceDate: dayjs('2026-10-18') },
      result(),
    );
    expect(fill.header.supplierId).toBeUndefined();
    expect(fill.header.invoiceNo).toBeUndefined();
    expect(fill.header.invoiceDate).toBeUndefined();
    expect(fill.header.invoiceSeries).toBe('1C26TAA');
    expect(fill.notes).toHaveLength(2);
    expect(fill.notes[0]).toContain('khác nhà cung cấp đang chọn');
    expect(fill.notes[1]).toContain('0000131');
  });

  it('nhà cung cấp không có / trùng MST: không chọn, ghi chú để người dùng chọn', () => {
    const notFound = purchaseOcrFill(
      {},
      result({
        header: {
          ...result().header,
          supplier: match({ status: 'NOT_FOUND', matchedBy: null, rawText: 'Công ty X (0399999901)' }),
        },
      }),
    );
    expect(notFound.header.supplierId).toBeUndefined();
    expect(notFound.notes[0]).toContain('0399999901');
  });

  it('dòng hàng: khớp mã thì điền đủ; gán theo tên, ứng viên, thuế không có, thành tiền lệch thì có cảnh báo', () => {
    const fill = purchaseOcrFill(
      {},
      result({
        lines: [
          line({
            product: match({ id: 1, code: 'GL1', name: 'Glucose' }),
            unit: match({ matchedBy: 'UNIT', id: 3, code: 'HOP', name: 'Hộp', rawText: 'Hp' }),
            taxRate: match({ matchedBy: 'RATE', id: 5, code: 'VAT10', name: 'VAT 10%' }),
            quantity: value('10'),
            unitPrice: value('356000'),
          }),
          line({
            product: match({ matchedBy: 'NAME', reviewRequired: true, id: 2, code: 'VT1', name: 'Ống', rawText: 'ng' }),
            taxRate: match({ status: 'NOT_FOUND', matchedBy: null, rawText: '8%' }),
            unit: match({ status: 'NOT_FOUND', matchedBy: null, rawText: 'Khay' }),
            quantity: value('2', 'LOW_CONFIDENCE'),
            amountCheck: { status: 'MISMATCH', calculated: '100000', printed: '150000', warnings: ['Lệch.'] },
          }),
          line({
            product: match({
              status: 'AMBIGUOUS',
              matchedBy: null,
              rawText: 'Triglycerides',
              candidates: [
                { id: 10, code: 'GL8S104', name: 'Triglycerides' },
                { id: 11, code: 'GLM114', name: 'Triglycerides' },
              ],
            }),
          }),
        ],
      }),
    );
    const [first, second, third] = fill.lines as [OcrLineValues, OcrLineValues, OcrLineValues];
    expect(first).toMatchObject({ productId: 1, productLabel: 'GL1 - Glucose', unitId: 3, taxRateId: 5 });
    expect(first.quantity).toBe(10);
    expect(first.ocrHints).toEqual([]);

    expect(second.productId).toBe(2);
    expect(second.unitId).toBeUndefined();
    expect(second.taxRateId).toBeUndefined();
    expect(second.ocrLevel).toBe('error');
    expect(second.ocrHints).toEqual([
      'Hàng gán theo tên trên chứng từ "ng": kiểm tra đúng mã hàng.',
      'ĐVT trên chứng từ "Khay" chưa xác định: chọn ĐVT.',
      'Thuế suất 8% không có hoặc trùng trong danh mục: chọn thuế suất.',
      'Số lượng đọc chưa chắc chắn: kiểm tra lại.',
      'Lệch.',
    ]);

    expect(third.productId).toBeUndefined();
    expect(third.ocrHints?.[0]).toContain('GL8S104 - Triglycerides; GLM114 - Triglycerides');
  });

  it('cảnh báo cả chứng từ và lệch tổng tiền vào ghi chú', () => {
    const fill = purchaseOcrFill(
      {},
      result({
        warnings: ['Chứng từ ghi loại tiền USD.'],
        totals: {
          ...result().totals,
          check: { status: 'MISMATCH', calculated: null, printed: null, warnings: ['Tổng lệch.'] },
        },
      }),
    );
    expect(fill.notes).toEqual(['Chứng từ ghi loại tiền USD.', 'Tổng lệch.']);
  });
});
