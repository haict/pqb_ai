import { QUANTITY_DIGITS, formatDateTime } from '../../shared/format';
import { formatDecimal } from '../../shared/inventorySlip/slipContent';
import { displayDate } from '../../shared/reportPeriod';
import { asciiFileName, type ReportRow, type TableReport } from '../../shared/report/tableReport';
import { statusLabel } from '../inventory/labels';
import { COMPLAINT_STATUSES, DECISIONS, DELIVERY_STATUSES } from '../sales/labels';
import type { ComplaintDecision, ComplaintStatus, DeliveryStatus } from '../sales/types';
import type { OrderProgress, PriceHistory, SalesJournal, StockBacklog, SupplierDefects } from './types';

/** Chuyển dữ liệu báo cáo mua / bán đợt 10B (BR-RPT-25, 26) sang bảng báo cáo dùng chung. */

const MONEY = 0;
const QUANTITY = QUANTITY_DIGITS;
const PRICE = 2;

function price(value: number | null) {
  return value === null ? '' : formatDecimal(value, PRICE);
}

function period(from: string, to: string) {
  return `Từ ngày ${displayDate(from)} đến ngày ${displayDate(to)}`;
}

function generatedNote(generatedAt: string) {
  return `Số liệu lấy lúc ${formatDateTime(generatedAt)}.`;
}

/** Đường dẫn mở chứng từ nguồn (BR-RPT-11). */
export function orderLink(documentType: string, documentId: number) {
  switch (documentType) {
    case 'DMH':
      return `/purchasing/orders?id=${documentId}`;
    case 'DBH':
      return `/sales/orders?id=${documentId}`;
    case 'TLB':
    case 'GGB':
      return `/sales/returns?id=${documentId}`;
    default:
      return `/purchasing/returns?id=${documentId}`;
  }
}

export function orderProgressReport(progress: OrderProgress): TableReport {
  const sales = progress.documentType === 'DBH';
  const partner = sales ? 'Khách hàng' : 'Nhà cung cấp';
  return {
    title: sales ? 'TÌNH HÌNH THỰC HIỆN ĐƠN BÁN' : 'TÌNH HÌNH THỰC HIỆN ĐƠN MUA',
    subtitle: period(progress.dateFrom, progress.dateTo),
    columns: [
      { title: 'Ngày đơn', width: 11 },
      { title: 'Số đơn', width: 18 },
      { title: partner, width: 30 },
      { title: 'Trạng thái', width: 12 },
      { title: sales ? 'Giao hàng' : 'Ngày dự kiến về', width: 14 },
      { title: 'Trễ hẹn (ngày)', digits: 0, width: 9 },
      { title: 'Giá trị đơn', digits: MONEY, width: 14 },
      { title: 'Trả lại / giảm giá', digits: MONEY, width: 14 },
      { title: sales ? 'Đã thu' : 'Đã thanh toán', digits: MONEY, width: 14 },
      { title: sales ? 'Còn phải thu' : 'Còn phải trả', digits: MONEY, width: 14 },
    ],
    rows: [
      ...progress.rows.map((row): ReportRow => ({
        cells: [
          displayDate(row.documentDate),
          row.documentNo,
          `${row.partnerCode} - ${row.partnerName}`,
          statusLabel(row.status),
          sales
            ? (DELIVERY_STATUSES[row.deliveryStatus as DeliveryStatus]?.label ?? '')
            : displayDate(row.expectedDate),
          row.overdueDays,
          row.totalAmount,
          row.returnedAmount,
          row.paidAmount,
          row.remainingAmount,
        ],
        link: { column: 1, to: orderLink(progress.documentType, row.documentId) },
      })),
      {
        cells: [
          null,
          `Cộng: ${progress.total.orderCount} đơn`,
          null,
          null,
          null,
          progress.total.overdueCount,
          progress.total.totalAmount,
          progress.total.returnedAmount,
          progress.total.paidAmount,
          progress.total.remainingAmount,
        ],
        bold: true,
      },
    ],
    notes: [
      'Tiền gồm thuế. Trễ hẹn: đơn chưa ghi sổ (hoặc đơn bán khách chưa nhận) quá ngày dự kiến; dòng tổng là số đơn trễ.',
      generatedNote(progress.generatedAt),
    ],
    fileName: asciiFileName(
      `${sales ? 'Thuc-hien-don-ban' : 'Thuc-hien-don-mua'}_${progress.dateFrom}_${progress.dateTo}`,
    ),
  };
}

export function priceHistoryReport(history: PriceHistory): TableReport {
  const rows: ReportRow[] = [];
  history.summaries.forEach((summary) => {
    history.rows
      .filter((row) => row.productId === summary.productId)
      .forEach((row) =>
        rows.push({
          cells: [
            row.productCode,
            displayDate(row.postingDate),
            row.documentNo,
            `${row.supplierCode} - ${row.supplierName}`,
            row.unitName,
            row.quantity,
            row.unitPrice,
            row.discountPercent,
            row.netUnitPrice,
            row.netBasePrice,
          ],
          link: { column: 2, to: orderLink('DMH', row.documentId) },
        }),
      );
    rows.push({
      cells: [
        summary.productCode,
        null,
        `${summary.purchaseCount} lần mua`,
        `Giá sau CK / ${summary.baseUnitName}: thấp nhất ${price(summary.minPrice)}, cao nhất ${price(
          summary.maxPrice,
        )}, gần nhất ${price(summary.lastPrice)} (${summary.lastSupplierName}, ${displayDate(summary.lastDate)})`,
        null,
        null,
        null,
        null,
        null,
        null,
      ],
      bold: true,
    });
  });
  return {
    title: 'LỊCH SỬ GIÁ MUA',
    subtitle: period(history.dateFrom, history.dateTo),
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Ngày hạch toán', width: 11 },
      { title: 'Số đơn mua', width: 18 },
      { title: 'Nhà cung cấp', width: 30 },
      { title: 'ĐVT', width: 8 },
      { title: 'Số lượng', digits: QUANTITY, width: 11 },
      { title: 'Đơn giá', digits: PRICE, width: 14 },
      { title: 'CK (%)', digits: 2, width: 8 },
      { title: 'Giá sau CK', digits: PRICE, width: 14 },
      { title: 'Giá sau CK / ĐV cơ sở', digits: PRICE, width: 14 },
    ],
    rows,
    notes: [
      'Đơn mua đang ghi sổ (không tính đơn đã đảo); dòng tổng của mỗi hàng so sánh giá sau chiết khấu theo đơn vị cơ sở.',
      generatedNote(history.generatedAt),
    ],
    fileName: asciiFileName(`Lich-su-gia-mua_${history.dateFrom}_${history.dateTo}`),
  };
}

export function supplierDefectsReport(defects: SupplierDefects): TableReport {
  const rows: ReportRow[] = [];
  defects.suppliers.forEach((supplier) => {
    rows.push({
      cells: [
        supplier.supplierCode
          ? `NCC ${supplier.supplierCode} - ${supplier.supplierName}`
          : 'Không xác định NCC',
        `${supplier.complaintCount} phiếu, ${supplier.productCount} mặt hàng`,
        null,
        null,
        null,
        null,
        supplier.quantity,
        null,
        null,
      ],
      bold: true,
    });
    defects.rows
      .filter((row) => row.supplierId === supplier.supplierId)
      .forEach((row) =>
        rows.push({
          cells: [
            displayDate(row.receivedDate),
            row.complaintNo,
            `${row.customerCode} - ${row.customerName}`,
            `${row.productCode} - ${row.productName}`,
            row.lotNo ?? '',
            row.serialNo ?? '',
            row.quantity,
            COMPLAINT_STATUSES[row.status as ComplaintStatus]?.label ?? row.status,
            row.decision ? (DECISIONS[row.decision as ComplaintDecision] ?? row.decision) : '',
          ],
          link: { column: 1, to: `/sales/complaints?id=${row.complaintId}` },
        }),
      );
  });
  return {
    title: 'HÀNG LỖI THEO NHÀ CUNG CẤP',
    subtitle: period(defects.dateFrom, defects.dateTo),
    columns: [
      { title: 'Ngày nhận', width: 11 },
      { title: 'Số phiếu', width: 18 },
      { title: 'Khách hàng', width: 26 },
      { title: 'Hàng hóa', width: 30 },
      { title: 'Số lô', width: 12 },
      { title: 'Serial', width: 14 },
      { title: 'Số lượng lỗi', digits: QUANTITY, width: 11 },
      { title: 'Trạng thái', width: 12 },
      { title: 'Quyết định', width: 12 },
    ],
    rows,
    notes: [
      'Nhà cung cấp truy theo lô: lần nhập mua đầu tiên của lô. Phiếu không ghi lô hoặc lô không từ đơn mua: "Không xác định NCC".',
      generatedNote(defects.generatedAt),
    ],
    fileName: asciiFileName(`Hang-loi-theo-NCC_${defects.dateFrom}_${defects.dateTo}`),
  };
}

const JOURNAL_TYPES: Record<string, string> = { DBH: 'Bán hàng', TLB: 'Trả lại', GGB: 'Giảm giá' };

export function salesJournalReport(journal: SalesJournal): TableReport {
  return {
    title: 'SỔ NHẬT KÝ BÁN HÀNG',
    subtitle: period(journal.dateFrom, journal.dateTo),
    columns: [
      { title: 'Ngày hạch toán', width: 11 },
      { title: 'Số chứng từ', width: 18 },
      { title: 'Ngày chứng từ', width: 11 },
      { title: 'Số hóa đơn', width: 12 },
      { title: 'Khách hàng', width: 28 },
      { title: 'Diễn giải', width: 26 },
      { title: 'Phải thu', digits: MONEY, width: 14 },
      { title: 'Doanh thu hàng hóa', digits: MONEY, width: 14 },
      { title: 'Doanh thu dịch vụ', digits: MONEY, width: 14 },
      { title: 'Giảm trừ', digits: MONEY, width: 13 },
      { title: 'Thuế GTGT', digits: MONEY, width: 13 },
    ],
    rows: [
      ...journal.rows.map((row): ReportRow => ({
        cells: [
          displayDate(row.postingDate),
          row.documentNo,
          displayDate(row.documentDate),
          row.invoiceNo ?? '',
          `${row.customerCode} - ${row.customerName}`,
          `${row.reversal ? 'Đảo: ' : ''}${row.description ?? JOURNAL_TYPES[row.documentType] ?? row.documentType}`,
          row.receivable,
          row.goodsAmount,
          row.serviceAmount,
          row.deductionAmount,
          row.taxAmount,
        ],
        italic: row.reversal,
        link: { column: 1, to: orderLink(row.documentType, row.documentId) },
      })),
      {
        cells: [
          null,
          null,
          null,
          null,
          'Tổng cộng',
          null,
          journal.total.receivable,
          journal.total.goodsAmount,
          journal.total.serviceAmount,
          journal.total.deductionAmount,
          journal.total.taxAmount,
        ],
        bold: true,
      },
    ],
    notes: [
      'Lập từ chứng từ bán đã ghi sổ (đơn bán, trả lại, giảm giá); phải thu gồm thuế. Mẫu sổ theo hiện trạng TT133 [Chưa kiểm chứng], kế toán trưởng xác nhận.',
      generatedNote(journal.generatedAt),
    ],
    fileName: asciiFileName(`So-nhat-ky-ban-hang_${journal.dateFrom}_${journal.dateTo}`),
  };
}

export function stockBacklogReport(backlog: StockBacklog): TableReport {
  return {
    title: 'TỒN KHO SO VỚI ĐẶT HÀNG CHƯA GIAO',
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 32 },
      { title: 'ĐVT', width: 8 },
      { title: 'Kho', width: 10 },
      { title: 'Đặt chưa giao', digits: QUANTITY, width: 12 },
      { title: 'Số đơn', digits: 0, width: 8 },
      { title: 'Tồn thực tế', digits: QUANTITY, width: 12 },
      { title: 'Giữ chỗ', digits: QUANTITY, width: 11 },
      { title: 'Khả dụng', digits: QUANTITY, width: 11 },
      { title: 'Đang về', digits: QUANTITY, width: 11 },
      { title: 'Thiếu', digits: QUANTITY, width: 11 },
    ],
    rows: backlog.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.unitName,
        row.warehouseCode,
        row.ordered,
        row.orderCount,
        row.onHand,
        row.reserved,
        row.available,
        row.incoming,
        row.shortage,
      ],
      bold: row.shortage > 0,
    })),
    notes: [
      'Đặt chưa giao: đơn bán đã gửi / đã duyệt chưa ghi sổ. Thiếu = đặt chưa giao − tồn thực tế − đang về (không âm). Đơn vị cơ sở.',
      generatedNote(backlog.generatedAt),
    ],
    fileName: asciiFileName(`Ton-so-voi-dat-hang_${backlog.generatedAt.slice(0, 10)}`),
  };
}
