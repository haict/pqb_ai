import {
  AlignmentType,
  BorderStyle,
  Document,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType,
} from 'docx';
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

const FONT = 'Times New Roman';
/** Cỡ chữ docx tính theo nửa point: 22 = 11pt, 20 = 10pt. */
const BODY_SIZE = 22;
const TABLE_SIZE = 20;
/** A4 dọc 11906 twip, lề 850 twip (1,5 cm) mỗi bên → bề rộng nội dung 10206 twip. */
const PAGE_MARGIN = 850;
const CONTENT_WIDTH = 10206;
const COLUMN_WIDTHS = [500, 1000, 2100, 900, 950, 700, 600, 950, 1200, 1306];
const NO_BORDER = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
const NO_BORDERS = {
  top: NO_BORDER,
  bottom: NO_BORDER,
  left: NO_BORDER,
  right: NO_BORDER,
  insideHorizontal: NO_BORDER,
  insideVertical: NO_BORDER,
};
const MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

type Align = (typeof AlignmentType)[keyof typeof AlignmentType];

interface RunStyle {
  bold?: boolean;
  italic?: boolean;
  size?: number;
  align?: Align;
}

/** Đoạn văn; ký tự xuống dòng trong {@code text} thành ngắt dòng trong cùng đoạn. */
function paragraph(text: string, style: RunStyle = {}) {
  return new Paragraph({
    alignment: style.align,
    children: text.split('\n').map(
      (part, index) =>
        new TextRun({
          text: part,
          break: index > 0 ? 1 : undefined,
          bold: style.bold,
          italics: style.italic,
          size: style.size ?? BODY_SIZE,
          font: FONT,
        }),
    ),
  });
}

function cell(text: string, style: RunStyle & { columnSpan?: number; width?: number } = {}) {
  return new TableCell({
    columnSpan: style.columnSpan,
    width: style.width ? { size: style.width, type: WidthType.DXA } : undefined,
    margins: { left: 60, right: 60 },
    children: [paragraph(text, { size: TABLE_SIZE, ...style })],
  });
}

function spanWidth(from: number, count: number) {
  return COLUMN_WIDTHS.slice(from, from + count).reduce((sum, width) => sum + width, 0);
}

function headerTable(slip: InventorySlipView) {
  const left = CONTENT_WIDTH - 2200;
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [left, 2200],
    borders: NO_BORDERS,
    rows: [
      new TableRow({
        children: [
          new TableCell({
            width: { size: left, type: WidthType.DXA },
            children: [
              paragraph(slip.title, { bold: true, size: 32, align: AlignmentType.CENTER }),
              paragraph(longDate(slip.slipDate), { bold: true, italic: true, align: AlignmentType.CENTER }),
              paragraph(`Số: ${slip.slipNo ?? ''}`, { align: AlignmentType.CENTER }),
            ],
          }),
          new TableCell({
            width: { size: 2200, type: WidthType.DXA },
            children: [
              paragraph(`Nợ: ${slip.debitAccounts.join(', ')}`),
              paragraph(`Có: ${slip.creditAccounts.join(', ')}`),
            ],
          }),
        ],
      }),
    ],
  });
}

function linesTable(slip: InventorySlipView) {
  const right = { align: AlignmentType.RIGHT };
  const strongRight = { align: AlignmentType.RIGHT, bold: true };
  const header = (labels: string[]) =>
    new TableRow({
      tableHeader: true,
      children: labels.map((label, index) =>
        cell(label, { bold: true, align: AlignmentType.CENTER, width: COLUMN_WIDTHS[index] }),
      ),
    });
  const summary = (left: string, label: string, amount: number) =>
    new TableRow({
      children: [
        cell(left, { columnSpan: 6, width: spanWidth(0, 6) }),
        cell(label, { columnSpan: 3, width: spanWidth(6, 3) }),
        cell(formatDecimal(amount, AMOUNT_DIGITS), strongRight),
      ],
    });
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: COLUMN_WIDTHS,
    rows: [
      header(SLIP_COLUMNS),
      header(SLIP_COLUMN_KEYS),
      ...slip.lines.map(
        (line) =>
          new TableRow({
            children: [
              cell(String(line.lineNo), { align: AlignmentType.CENTER }),
              cell(line.productCode ?? ''),
              cell(productText(line)),
              cell(line.lots.join('\n')),
              cell(line.expiries.join('\n')),
              cell(line.warehouseCode ?? ''),
              cell(line.unitName),
              cell(formatDecimal(line.quantity, QUANTITY_DIGITS), right),
              cell(formatDecimal(line.unitPrice, PRICE_DIGITS), right),
              cell(formatDecimal(line.amount, AMOUNT_DIGITS), right),
            ],
          }),
      ),
      new TableRow({
        children: [
          cell(''),
          cell(''),
          cell('Cộng', { bold: true, align: AlignmentType.CENTER }),
          ...Array.from({ length: 6 }, () => cell('')),
          cell(formatDecimal(slip.totalAmount, AMOUNT_DIGITS), strongRight),
        ],
      }),
      summary('', 'Cộng tiền hàng:', slip.totalAmount),
      summary(`Thuế suất GTGT: ${taxRatesText(slip)}`, 'Tiền thuế GTGT:', slip.taxAmount),
      summary('', 'Tổng tiền thanh toán:', slip.paymentAmount),
    ],
  });
}

function signersTable(slip: InventorySlipView) {
  const width = Math.floor(CONTENT_WIDTH / Math.max(slip.signers.length, 1));
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: slip.signers.map(() => width),
    borders: NO_BORDERS,
    rows: [
      new TableRow({
        children: slip.signers.map(
          (signer) =>
            new TableCell({
              width: { size: width, type: WidthType.DXA },
              children: [
                paragraph(signer, { bold: true, align: AlignmentType.CENTER }),
                paragraph(SIGN_HINT, { italic: true, align: AlignmentType.CENTER }),
              ],
            }),
        ),
      }),
    ],
  });
}

/** Phiếu nhập / xuất kho dạng .docx (A4 dọc), cùng bố cục bản in để người dùng chỉnh sửa tiếp. */
export function slipToWordDocument(slip: InventorySlipView) {
  return new Document({
    creator: slip.companyName ?? undefined,
    title: slip.title,
    sections: [
      {
        properties: {
          page: {
            margin: { top: PAGE_MARGIN, bottom: PAGE_MARGIN, left: PAGE_MARGIN, right: PAGE_MARGIN },
          },
        },
        children: [
          paragraph(slip.companyName ?? '', { bold: true }),
          paragraph(slip.companyAddress ?? ''),
          paragraph(''),
          headerTable(slip),
          paragraph(''),
          ...slip.infoRows.map((info) => paragraph(`- ${info}`)),
          linesTable(slip),
          paragraph(''),
          paragraph(`- Số tiền bằng chữ: ${moneyInWords(slip.paymentAmount)}`),
          paragraph(ORIGINAL_DOCUMENTS_LINE),
          paragraph(SIGN_DATE_LINE, { italic: true, align: AlignmentType.RIGHT }),
          signersTable(slip),
        ],
      },
    ],
  });
}

export async function slipToWord(slip: InventorySlipView): Promise<Blob> {
  const blob = await Packer.toBlob(slipToWordDocument(slip));
  return new Blob([blob], { type: MIME });
}
