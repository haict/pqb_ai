import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { moneyInWords } from '../moneyWords';
import {
  AMOUNT_DIGITS,
  formatDecimal,
  longDate,
  ORIGINAL_DOCUMENTS_LINE,
  PRICE_DIGITS,
  productText,
  QUANTITY_DIGITS,
  SIGN_DATE_LINE,
  SIGN_HINT,
  SLIP_COLUMN_KEYS,
  SLIP_COLUMNS,
  taxRatesText,
  type InventorySlipView,
} from './slipContent';

const COLUMN_WIDTHS = [18, 45, '*', 48, 48, 32, 30, 46, 56, 62];

function summaryRow(left: string, label: string, amount: number): TableCell[] {
  return [
    { text: left, colSpan: 6 },
    '',
    '',
    '',
    '',
    '',
    { text: label, colSpan: 3 },
    '',
    '',
    { text: formatDecimal(amount, AMOUNT_DIGITS), alignment: 'right', bold: true },
  ];
}

function linesTable(slip: InventorySlipView): Content {
  const header = (labels: string[]): TableCell[] =>
    labels.map((label) => ({ text: label, bold: true, alignment: 'center' }));
  const right = (value: number, digits: number): TableCell => ({
    text: formatDecimal(value, digits),
    alignment: 'right',
  });
  return {
    margin: [0, 6, 0, 0],
    fontSize: 8.5,
    table: {
      headerRows: 2,
      widths: COLUMN_WIDTHS,
      body: [
        header(SLIP_COLUMNS),
        header(SLIP_COLUMN_KEYS),
        ...slip.lines.map((line): TableCell[] => [
          { text: String(line.lineNo), alignment: 'center' },
          line.productCode ?? '',
          productText(line),
          line.lots.join('\n'),
          line.expiries.join('\n'),
          line.warehouseCode ?? '',
          line.unitName,
          right(line.quantity, QUANTITY_DIGITS),
          right(line.unitPrice, PRICE_DIGITS),
          right(line.amount, AMOUNT_DIGITS),
        ]),
        [
          '',
          '',
          { text: 'Cộng', bold: true, alignment: 'center' },
          '',
          '',
          '',
          '',
          '',
          '',
          { text: formatDecimal(slip.totalAmount, AMOUNT_DIGITS), alignment: 'right', bold: true },
        ],
        summaryRow('', 'Cộng tiền hàng:', slip.totalAmount),
        summaryRow(`Thuế suất GTGT: ${taxRatesText(slip)}`, 'Tiền thuế GTGT:', slip.taxAmount),
        summaryRow('', 'Tổng tiền thanh toán:', slip.paymentAmount),
      ],
    },
  };
}

/** Định nghĩa tài liệu pdfmake (A4 dọc, font Roboto có đủ dấu tiếng Việt); tách riêng để kiểm thử không cần trình duyệt. */
export function slipPdfDefinition(slip: InventorySlipView): TDocumentDefinitions {
  return {
    pageSize: 'A4',
    pageMargins: [30, 30, 30, 30],
    info: { title: `${slip.title} ${slip.slipNo ?? ''}`.trim() },
    defaultStyle: { fontSize: 10, lineHeight: 1.15 },
    content: [
      { text: slip.companyName ?? '', bold: true },
      { text: slip.companyAddress ?? '' },
      {
        margin: [0, 10, 0, 0],
        columns: [
          {
            width: '*',
            stack: [
              { text: slip.title, fontSize: 16, bold: true, alignment: 'center' },
              { text: longDate(slip.slipDate), bold: true, italics: true, alignment: 'center' },
              { text: `Số: ${slip.slipNo ?? ''}`, alignment: 'center' },
            ],
          },
          {
            width: 110,
            stack: [`Nợ: ${slip.debitAccounts.join(', ')}`, `Có: ${slip.creditAccounts.join(', ')}`],
          },
        ],
      },
      { margin: [0, 10, 0, 0], stack: slip.infoRows.map((info) => `- ${info}`), lineHeight: 1.4 },
      linesTable(slip),
      {
        margin: [0, 10, 0, 0],
        text: ['- Số tiền bằng chữ: ', { text: moneyInWords(slip.paymentAmount), bold: true, italics: true }],
      },
      { text: ORIGINAL_DOCUMENTS_LINE, margin: [0, 4, 0, 0] },
      { text: SIGN_DATE_LINE, italics: true, alignment: 'right', margin: [0, 8, 0, 4] },
      {
        columns: slip.signers.map((signer) => ({
          width: '*',
          alignment: 'center',
          stack: [
            { text: signer, bold: true },
            { text: SIGN_HINT, italics: true },
          ],
        })),
      },
    ],
  };
}

interface PdfMake {
  addVirtualFileSystem(vfs: unknown): void;
  createPdf(definition: TDocumentDefinitions): { getBlob(): Promise<Blob> };
}

/** pdfmake bản trình duyệt là UMD: lấy {@code default} nếu bundler bọc lại, ngược lại dùng chính module. */
function unwrap<T>(module: T | { default: T }): T {
  return typeof module === 'object' && module !== null && 'default' in module ? module.default : module;
}

export async function slipToPdf(slip: InventorySlipView): Promise<Blob> {
  const [pdfMakeModule, fontsModule] = await Promise.all([
    import('pdfmake/build/pdfmake'),
    import('pdfmake/build/vfs_fonts'),
  ]);
  const pdfMake = unwrap(pdfMakeModule as unknown as PdfMake | { default: PdfMake });
  pdfMake.addVirtualFileSystem(unwrap(fontsModule as unknown));
  return pdfMake.createPdf(slipPdfDefinition(slip)).getBlob();
}
