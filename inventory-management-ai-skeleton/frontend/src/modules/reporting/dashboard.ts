import { QUANTITY_DIGITS, formatDateTime } from '../../shared/format';
import { formatDecimal } from '../../shared/inventorySlip/slipContent';
import { displayDate } from '../../shared/reportPeriod';
import { asciiFileName, type ReportRow, type TableReport } from '../../shared/report/tableReport';
import { statusLabel } from '../inventory/labels';
import { DELIVERY_STATUSES } from '../sales/labels';
import type { DeliveryStatus } from '../sales/types';
import { orderLink } from './orderReports';
import type { BacklogDetails, DebtDetails, ExpiryDetails, TradeDetails } from './types';

/** Danh sách chi tiết của chỉ tiêu dashboard (BR-RPT-11) sang bảng báo cáo dùng chung: xem, In, Excel, PDF. */

const MONEY = 0;
const QUANTITY = QUANTITY_DIGITS;

const DOCUMENT_TYPES: Record<string, string> = {
  DBH: 'Đơn bán',
  TLB: 'Trả lại',
  GGB: 'Giảm giá',
  DMH: 'Đơn mua',
  THN: 'Trả NCC',
  GGM: 'Giảm giá mua',
};

function notes(generatedAt: string, extra: string[] = []) {
  return [...extra, `Số liệu lấy lúc ${formatDateTime(generatedAt)}.`];
}

/** Mở chứng từ nguồn của khoản công nợ; nguồn khác đơn mua / đơn bán (công nợ đầu kỳ...) chỉ hiện số. */
export function debtLink(sourceType: string, sourceId: number) {
  return sourceType === 'DBH' || sourceType === 'DMH' ? orderLink(sourceType, sourceId) : undefined;
}

/** {@code filterLabel}: dòng top đang xem (hàng, khách, NCC), trống khi xem cả chỉ tiêu. */
export function tradeDetailsReport(details: TradeDetails, filterLabel?: string): TableReport {
  const sales = details.kind === 'revenue';
  const title = sales ? 'CHI TIẾT DOANH THU THUẦN' : 'CHI TIẾT GIÁ TRỊ MUA THUẦN';
  return {
    title,
    subtitle: `Từ ngày ${displayDate(details.dateFrom)} đến ngày ${displayDate(details.dateTo)}${
      filterLabel ? ` - ${filterLabel}` : ''
    }`,
    columns: [
      { title: 'Ngày hạch toán', width: 11 },
      { title: 'Loại', width: 12 },
      { title: 'Số chứng từ', width: 20 },
      { title: sales ? 'Khách hàng' : 'Nhà cung cấp', width: 36 },
      { title: 'Giá trị chưa thuế', digits: MONEY, width: 16 },
    ],
    rows: [
      ...details.rows.map((row): ReportRow => ({
        cells: [
          displayDate(row.entryDate),
          `${DOCUMENT_TYPES[row.documentType] ?? row.documentType}${row.reversal ? ' (đảo)' : ''}`,
          row.documentNo,
          `${row.partnerCode} - ${row.partnerName}`,
          row.amount,
        ],
        italic: row.reversal,
        link: { column: 2, to: orderLink(row.documentType, row.documentId) },
      })),
      { cells: ['', '', '', 'Tổng cộng', details.total], bold: true },
    ],
    notes: notes(details.generatedAt, [
      'Chứng từ đã ghi sổ, sau chiết khấu, chưa thuế; trả lại / giảm giá ghi âm; chứng từ đảo ghi ngược dấu theo ngày đảo.',
    ]),
    fileName: asciiFileName(`${title}-${details.dateFrom}-${details.dateTo}`),
  };
}

export function debtDetailsReport(details: DebtDetails): TableReport {
  const receivable = details.debtType === 'RECEIVABLE';
  const title = receivable ? 'CÔNG NỢ PHẢI THU CÒN LẠI' : 'CÔNG NỢ PHẢI TRẢ CÒN LẠI';
  return {
    title,
    subtitle: `Đến ngày ${displayDate(details.today)}`,
    columns: [
      { title: 'Số chứng từ', width: 20 },
      { title: 'Số hóa đơn', width: 12 },
      { title: receivable ? 'Khách hàng' : 'Nhà cung cấp', width: 34 },
      { title: 'Ngày chứng từ', width: 11 },
      { title: 'Hạn thanh toán', width: 11 },
      { title: 'Quá hạn (ngày)', digits: 0, width: 9 },
      { title: 'Số tiền', digits: MONEY, width: 15 },
      { title: receivable ? 'Đã thu' : 'Đã trả', digits: MONEY, width: 15 },
      { title: 'Còn lại', digits: MONEY, width: 15 },
    ],
    rows: [
      ...details.rows.map((row): ReportRow => {
        const to = debtLink(row.sourceType, row.sourceId);
        return {
          cells: [
            row.sourceNo,
            row.invoiceNo,
            `${row.partnerCode} - ${row.partnerName}`,
            displayDate(row.documentDate),
            displayDate(row.dueDate),
            row.overdueDays > 0 ? row.overdueDays : null,
            row.originalAmount,
            row.settledAmount,
            row.remainingAmount,
          ],
          bold: row.overdueDays > 0,
          link: to ? { column: 0, to } : undefined,
        };
      }),
      {
        cells: [
          '',
          '',
          `Tổng cộng (quá hạn ${formatDecimal(details.overdueAmount, MONEY)})`,
          '',
          '',
          null,
          null,
          null,
          details.totalAmount,
        ],
        bold: true,
      },
    ],
    notes: notes(details.generatedAt, ['Dòng in đậm: đã quá hạn thanh toán.']),
    fileName: asciiFileName(`${title}-${details.today}`),
  };
}

export function expiryDetailsReport(details: ExpiryDetails): TableReport {
  const title = 'HÀNG ĐÃ / SẮP HẾT HẠN';
  return {
    title,
    subtitle: `Hạn dùng đến ngày ${displayDate(details.warningUntil)} (cảnh báo trước ${details.warningDays} ngày)`,
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 34 },
      { title: 'ĐVT', width: 8 },
      { title: 'Kho', width: 10 },
      { title: 'Số lô', width: 14 },
      { title: 'Hạn dùng', width: 11 },
      { title: 'Còn (ngày)', digits: 0, width: 9 },
      { title: 'Tồn', digits: QUANTITY, width: 12 },
    ],
    rows: details.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.unitName,
        row.warehouseCode,
        row.lotNo,
        displayDate(row.expiryDate),
        row.daysLeft,
        row.onHand,
      ],
      bold: row.daysLeft < 0,
    })),
    notes: notes(details.generatedAt, ['Dòng in đậm: đã quá hạn dùng. Số lượng theo đơn vị cơ sở.']),
    fileName: asciiFileName(`${title}-${details.today}`),
  };
}

export function backlogDetailsReport(details: BacklogDetails): TableReport {
  const sales = details.documentType === 'DBH';
  const title = sales ? 'ĐƠN BÁN CHƯA GIAO ĐỦ' : 'ĐƠN MUA CHƯA NHẬN';
  return {
    title,
    subtitle: `Đến ngày ${displayDate(details.today)}`,
    columns: [
      { title: 'Ngày đơn', width: 11 },
      { title: 'Số đơn', width: 20 },
      { title: sales ? 'Khách hàng' : 'Nhà cung cấp', width: 34 },
      { title: 'Trạng thái', width: 12 },
      ...(sales ? [{ title: 'Giao hàng', width: 12 }] : []),
      { title: 'Ngày dự kiến', width: 11 },
      { title: 'Trễ (ngày)', digits: 0, width: 9 },
      { title: 'Giá trị đơn', digits: MONEY, width: 15 },
    ],
    rows: [
      ...details.rows.map((row): ReportRow => ({
        cells: [
          displayDate(row.documentDate),
          row.documentNo,
          `${row.partnerCode} - ${row.partnerName}`,
          statusLabel(row.status),
          ...(sales ? [DELIVERY_STATUSES[row.deliveryStatus as DeliveryStatus]?.label ?? ''] : []),
          displayDate(row.expectedDate),
          row.overdueDays > 0 ? row.overdueDays : null,
          row.totalAmount,
        ],
        bold: row.overdueDays > 0,
        link: { column: 1, to: orderLink(details.documentType, row.documentId) },
      })),
      {
        cells: [
          '',
          '',
          `Cộng: ${details.rows.length} đơn`,
          '',
          ...(sales ? [''] : []),
          '',
          null,
          details.totalAmount,
        ],
        bold: true,
      },
    ],
    notes: notes(details.generatedAt, [
      sales
        ? 'Đơn đã gửi / duyệt chưa ghi sổ (chưa xuất kho) và đơn đã ghi sổ khách chưa nhận. Tiền gồm thuế.'
        : 'Đơn đã gửi / duyệt chưa ghi sổ (chưa nhập kho). Tiền gồm thuế, quy đổi VND.',
      'Dòng in đậm: đã quá ngày dự kiến.',
    ]),
    fileName: asciiFileName(`${title}-${details.today}`),
  };
}
