import { formatFixed } from '../format';
import { moneyInWords } from '../moneyWords';
/** Một dòng hàng trên phiếu kho; một dòng có thể xuất nhiều lô (mỗi lô / hạn dùng một dòng trong ô). */
export interface SlipLineView {
  lineNo: number;
  productCode: string | null;
  productName: string | null;
  /** Ghi chú dưới tên hàng (ví dụ danh sách serial). */
  productNote?: string;
  lots: string[];
  expiries: string[];
  warehouseCode: string | null;
  unitName: string;
  quantity: number;
  /** null: để trống (bản cài đặt chỉ quản lý kho, D-73). */
  unitPrice: number | null;
  amount: number | null;
}

/** Nội dung phiếu nhập / xuất kho theo mẫu doanh nghiệp; dùng chung cho bản in và file tải về. */
export interface InventorySlipView {
  title: string;
  companyName: string | null;
  companyAddress: string | null;
  slipDate: string | null;
  slipNo: string | null;
  debitAccounts: string[];
  creditAccounts: string[];
  /** Các dòng thông tin chung, in theo thứ tự, mỗi dòng bắt đầu bằng "- ". */
  infoRows: string[];
  lines: SlipLineView[];
  /** Cộng tiền hàng (trước thuế); null: để trống phần tiền (D-73). */
  totalAmount: number | null;
  /** Thuế suất GTGT (%) của các dòng; nhiều mức thì in cách nhau dấu phẩy. */
  taxRates: number[];
  taxAmount: number | null;
  /** Tổng tiền thanh toán = tiền hàng + thuế; số tiền bằng chữ đọc theo số này. */
  paymentAmount: number | null;
  signers: string[];
}

export const SLIP_COLUMNS = [
  'STT',
  'Mã hàng',
  'Tên hàng',
  'Số lô',
  'Hạn sử dụng',
  'Mã kho',
  'ĐVT',
  'Số lượng',
  'Đơn giá',
  'Thành tiền',
];
export const SLIP_COLUMN_KEYS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', '1', '2', '3'];
/** Số chữ số thập phân hiển thị: số lượng, đơn giá 2; thành tiền làm tròn đồng (D-05). */
export const QUANTITY_DIGITS = 2;
export const PRICE_DIGITS = 2;
export const AMOUNT_DIGITS = 0;
export const ORIGINAL_DOCUMENTS_LINE =
  '- Số chứng từ gốc kèm theo: ..............................................................';
export const SIGN_DATE_LINE = 'Ngày ..... tháng ..... năm ........';
export const SIGN_HINT = '(Ký, họ tên)';

/** null in ô trống (phần giá bị ẩn). */
export function formatDecimal(value: number | null, fractionDigits: number) {
  return value === null ? '' : formatFixed(value, fractionDigits);
}

/** Số tiền bằng chữ theo tổng thanh toán; trống khi phần giá bị ẩn. */
export function paymentInWords(slip: InventorySlipView) {
  return slip.paymentAmount === null ? '' : moneyInWords(slip.paymentAmount);
}

/**
 * Bản cài đặt chỉ quản lý kho (D-73, posting.mode = INVENTORY_ONLY): phiếu nhập / xuất kho để trống đơn giá, thành
 * tiền, thuế suất, tiền thuế, tổng thanh toán và số tiền bằng chữ; giữ nguyên số lượng, lô, hạn dùng.
 */
export function withoutPrices(slip: InventorySlipView): InventorySlipView {
  return {
    ...slip,
    lines: slip.lines.map((line) => ({ ...line, unitPrice: null, amount: null })),
    totalAmount: null,
    taxRates: [],
    taxAmount: null,
    paymentAmount: null,
  };
}

/** "Ngày 07 tháng 10 năm 2026" từ ngày dạng YYYY-MM-DD. */
export function longDate(value: string | null) {
  if (!value) {
    return '';
  }
  const [year, month, day] = value.split('-');
  return `Ngày ${day} tháng ${month} năm ${year}`;
}

export function taxRatesText(slip: InventorySlipView) {
  return slip.taxRates.map((rate) => `${formatDecimal(rate, 0)}%`).join(', ');
}

/** Tên hàng kèm ghi chú (serial) xuống dòng; dùng cho file tải về, nơi một ô chỉ chứa văn bản. */
export function productText(line: SlipLineView) {
  return [line.productName ?? '', line.productNote].filter(Boolean).join('\n');
}

/** Tên file không dấu, không ký tự đặc biệt: "Phieu-nhap-kho_NK-2026-0001.xlsx". */
export function slipFileName(slip: InventorySlipView, extension: string) {
  const ascii = (value: string) =>
    value
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .replace(/[^A-Za-z0-9-]+/g, '-')
      .replace(/^-+|-+$/g, '');
  const title = ascii(slip.title.toLowerCase());
  const name = title.charAt(0).toUpperCase() + title.slice(1);
  return `${name}${slip.slipNo ? `_${ascii(slip.slipNo)}` : ''}.${extension}`;
}

/** Chia cột chữ ký đều trên {@code columns} cột; cột cuối nhận phần dư. Trả [cột bắt đầu (từ 0), số cột]. */
export function signerSpans(count: number, columns: number): [number, number][] {
  const span = Math.max(1, Math.floor(columns / Math.max(count, 1)));
  return Array.from({ length: count }, (_, index) => [
    index * span,
    index === count - 1 ? columns - index * span : span,
  ]);
}

export function saveBlob(blob: Blob, fileName: string) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}
