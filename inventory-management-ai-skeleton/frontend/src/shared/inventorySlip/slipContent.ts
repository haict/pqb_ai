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
  unitPrice: number;
  amount: number;
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
  /** Cộng tiền hàng (trước thuế). */
  totalAmount: number;
  /** Thuế suất GTGT (%) của các dòng; nhiều mức thì in cách nhau dấu phẩy. */
  taxRates: number[];
  taxAmount: number;
  /** Tổng tiền thanh toán = tiền hàng + thuế; số tiền bằng chữ đọc theo số này. */
  paymentAmount: number;
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

export function formatDecimal(value: number, fractionDigits: number) {
  return new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
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
