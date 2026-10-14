import ExcelJS from 'exceljs';
import type { PurchaseLedger, PurchaseLedgerLine } from '../types';
import {
  cellText,
  lineCells,
  purchaseDocumentLink,
  purchaseLedgerFileName,
  subtitle,
  totalCells,
} from './purchaseLedgerContent';
import { purchaseLedgerExcelBuffer, purchaseLedgerPdfDefinition } from './purchaseLedgerExport';

const line: PurchaseLedgerLine = {
  postingDate: '2026-01-07',
  documentType: 'DMH',
  documentId: 21,
  documentNo: 'NK00670/24',
  documentDate: '2026-01-07',
  invoiceDate: '2026-01-07',
  invoiceNo: '00000020',
  productId: 9,
  productCode: 'GL8S107_T',
  productName: 'AST (6x66ml/6x16.5 ml)',
  unitName: 'Hộp',
  purchaseQuantity: 1,
  unitPrice: 2900000,
  purchaseAmount: 2900000,
  discountAmount: 0,
  returnQuantity: null,
  returnAmount: null,
  priceReductionAmount: null,
  reversal: false,
};

const ledger: PurchaseLedger = {
  companyName: 'Công ty mẫu',
  companyAddress: 'Hà Nội',
  dateFrom: '2026-01-01',
  dateTo: '2026-10-09',
  generatedAt: '2026-10-09T03:00:00Z',
  lines: [
    line,
    { ...line, documentId: 22, documentNo: 'NK00707/24', purchaseQuantity: 3, purchaseAmount: 8700000 },
  ],
  total: {
    lineCount: 2,
    purchaseQuantity: 4,
    purchaseAmount: 11600000,
    discountAmount: 0,
    returnQuantity: 0,
    returnAmount: 0,
    priceReductionAmount: 0,
  },
};

describe('sổ chi tiết mua hàng', () => {
  it('cột theo màn hình hiện trạng: số lượng, đơn giá 2 chữ số, tiền làm tròn đồng; dòng tổng có số dòng', () => {
    const cells = lineCells(line);
    expect(cellText(cells, 2)).toBe('NK00670/24');
    expect(cellText(cells, 8)).toBe('1,00');
    expect(cellText(cells, 9)).toBe('2.900.000,00');
    expect(cellText(cells, 10)).toBe('2.900.000');
    expect(cellText(cells, 12)).toBe('');
    const totals = totalCells(ledger);
    expect(cellText(totals, 0)).toBe('Số dòng = 2');
    expect(cellText(totals, 8)).toBe('4,00');
    expect(cellText(totals, 10)).toBe('11.600.000');
  });

  it('tiêu đề có tên mặt hàng khi sổ chỉ có một hàng', () => {
    expect(subtitle(ledger)).toBe('Mặt hàng: AST (6x66ml/6x16.5 ml); Từ ngày 01/01/2026 đến ngày 09/10/2026');
    expect(subtitle({ ...ledger, lines: [line, { ...line, productId: 10 }] })).toBe(
      'Từ ngày 01/01/2026 đến ngày 09/10/2026',
    );
  });

  it('BR-RPT-11: số chứng từ mở đơn mua hoặc trả hàng / giảm giá', () => {
    expect(purchaseDocumentLink({ documentType: 'DMH', documentId: 21 })).toBe('/purchasing/orders?id=21');
    expect(purchaseDocumentLink({ documentType: 'GGM', documentId: 4 })).toBe('/purchasing/returns?id=4');
  });

  it('tên file theo kỳ, Excel giữ ô số, PDF khổ ngang', async () => {
    expect(purchaseLedgerFileName(ledger, 'pdf')).toBe('So-chi-tiet-mua-hang_2026-01-01_2026-10-09.pdf');
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await purchaseLedgerExcelBuffer(ledger));
    const values: unknown[] = [];
    workbook.worksheets[0]?.eachRow((row) => row.eachCell((cell) => values.push(cell.value)));
    expect(values).toContain('SỔ CHI TIẾT MUA HÀNG');
    expect(values).toContain('NK00707/24');
    expect(values).toContain(11600000);
    expect(purchaseLedgerPdfDefinition(ledger).pageOrientation).toBe('landscape');
  });
});
