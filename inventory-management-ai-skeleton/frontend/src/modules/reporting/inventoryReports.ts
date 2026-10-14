import {
  asciiFileName,
  type ReportColumn,
  type ReportRow,
  type TableReport,
} from '../../shared/report/tableReport';
import { formatDateTime } from '../../shared/format';
import { displayDate } from '../../shared/reportPeriod';
import { TRANSACTION_TYPES } from '../inventory/labels';
import type { TransactionType } from '../inventory/types';
import { QUALITY_STATUSES } from '../masterdata/labels';
import type { QualityStatus } from '../masterdata/types';
import type {
  CustomerTrace,
  ExceptionReason,
  InventoryMovement,
  LotTrace,
  MissingDocuments,
  SlowMoving,
  StockCard,
  StockExceptions,
  TraceLine,
} from './types';

/** Chuyển dữ liệu báo cáo kho (BR-RPT-21..24) sang bảng báo cáo dùng chung cho màn hình, bản in, Excel, PDF. */

const QUANTITY = 3;
export const REASON_LABELS: Record<ExceptionReason, string> = {
  EXPIRED: 'Hết hạn dùng',
  QUALITY: 'Trạng thái chất lượng',
  BLOCKED: 'Lô / serial bị khóa',
};

export function transactionLabel(type: string) {
  return TRANSACTION_TYPES[type as TransactionType] ?? type;
}

export function qualityLabel(status: string) {
  return QUALITY_STATUSES[status as QualityStatus]?.label ?? status;
}

/** Phiếu kho của dòng sổ kho (truy ngược chứng từ, BR-RPT-11). */
export function inventoryDocumentLink(referenceId: number) {
  return `/inventory/documents/${referenceId}`;
}

function period(from: string, to: string) {
  return `Từ ngày ${displayDate(from)} đến ngày ${displayDate(to)}`;
}

function generatedNote(generatedAt: string) {
  return `Số liệu lấy lúc ${formatDateTime(generatedAt)}.`;
}

function fileName(title: string, suffix: string) {
  return asciiFileName(`${title}_${suffix}`);
}

export function stockCardReport(card: StockCard): TableReport {
  const columns: ReportColumn[] = [
    { title: 'Ngày hạch toán', width: 11 },
    { title: 'Số chứng từ', width: 18 },
    { title: 'Diễn giải', width: 26 },
    { title: 'Đối tượng', width: 26 },
    { title: 'Kho', width: 9 },
    { title: 'Số lô', width: 12 },
    { title: 'Hạn dùng', width: 11 },
    { title: 'Serial', width: 14 },
    { title: 'Nhập', digits: QUANTITY, width: 11 },
    { title: 'Xuất', digits: QUANTITY, width: 11 },
    { title: 'Tồn', digits: QUANTITY, width: 11 },
  ];
  const blank = (label: string, values: [number | null, number | null, number]): ReportRow => ({
    cells: [null, null, label, null, null, null, null, null, ...values],
    bold: true,
  });
  const rows: ReportRow[] = [
    blank('Tồn đầu kỳ', [null, null, card.opening]),
    ...card.lines.map((line): ReportRow => ({
      cells: [
        displayDate(line.postingDate),
        line.referenceNo,
        `${line.reversal ? 'Đảo: ' : ''}${transactionLabel(line.transactionType)}${
          line.qualityStatus !== 'PASSED' ? ` (${qualityLabel(line.qualityStatus)})` : ''
        }`,
        line.partnerName ?? '',
        line.warehouseCode,
        line.lotNo ?? '',
        displayDate(line.expiryDate),
        line.serialNo ?? '',
        line.inQuantity,
        line.outQuantity,
        line.balance,
      ],
      italic: line.reversal,
      link: { column: 1, to: inventoryDocumentLink(line.referenceId) },
    })),
    blank('Cộng phát sinh', [card.totalIn, card.totalOut, card.closing]),
  ];
  return {
    title: 'THẺ KHO',
    subtitle: `Hàng: ${card.productCode} - ${card.productName}; ĐVT: ${card.unitName}; ${period(card.dateFrom, card.dateTo)}`,
    columns,
    rows,
    notes: ['Số lượng theo đơn vị cơ sở của hàng.', generatedNote(card.generatedAt)],
    fileName: fileName('The-kho', `${card.productCode}_${card.dateFrom}_${card.dateTo}`),
  };
}

export function movementReport(movement: InventoryMovement): TableReport {
  return {
    title: 'TỔNG HỢP NHẬP - XUẤT - TỒN',
    subtitle: period(movement.dateFrom, movement.dateTo),
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 34 },
      { title: 'ĐVT', width: 8 },
      { title: 'Kho', width: 10 },
      { title: 'Tồn đầu kỳ', digits: QUANTITY, width: 12 },
      { title: 'Nhập trong kỳ', digits: QUANTITY, width: 12 },
      { title: 'Xuất trong kỳ', digits: QUANTITY, width: 12 },
      { title: 'Tồn cuối kỳ', digits: QUANTITY, width: 12 },
    ],
    rows: movement.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.unitName,
        row.warehouseCode,
        row.opening,
        row.inQuantity,
        row.outQuantity,
        row.closing,
      ],
    })),
    notes: [
      'Số lượng theo đơn vị cơ sở; đảo chứng từ trừ vào chiều của chứng từ gốc; chuyển trạng thái chất lượng không tính.',
      ...(movement.valueAvailable
        ? []
        : ['Giá trị nhập - xuất - tồn chưa có: giá vốn được tính từ Giai đoạn 8.']),
      generatedNote(movement.generatedAt),
    ],
    fileName: fileName('Nhap-xuat-ton', `${movement.dateFrom}_${movement.dateTo}`),
  };
}

export function slowMovingReport(report: SlowMoving): TableReport {
  return {
    title: 'HÀNG TỒN LÂU',
    subtitle: `Không xuất từ ${report.thresholdDays} ngày trở lên (trước ngày ${displayDate(report.cutoffDate)})`,
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 34 },
      { title: 'ĐVT', width: 8 },
      { title: 'Kho', width: 10 },
      { title: 'Tồn', digits: QUANTITY, width: 12 },
      { title: 'Nhập gần nhất', width: 12 },
      { title: 'Xuất gần nhất', width: 12 },
      { title: 'Số ngày không xuất', digits: 0, width: 12 },
    ],
    rows: report.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.unitName,
        row.warehouseCode,
        row.onHand,
        displayDate(row.lastInDate),
        displayDate(row.lastOutDate),
        row.idleDays,
      ],
    })),
    notes: ['Chưa từng xuất: tính từ lần nhập gần nhất.', generatedNote(report.generatedAt)],
    fileName: fileName('Hang-ton-lau', `${report.thresholdDays}-ngay`),
  };
}

export function exceptionsReport(report: StockExceptions): TableReport {
  return {
    title: 'HÀNG NGOẠI LỆ',
    subtitle: `Hết hạn dùng, chất lượng khác ĐẠT, lô / serial bị khóa tại ngày ${displayDate(report.asOf)}`,
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 30 },
      { title: 'Kho', width: 10 },
      { title: 'Số lô', width: 12 },
      { title: 'Hạn dùng', width: 11 },
      { title: 'Serial', width: 14 },
      { title: 'Trạng thái', width: 14 },
      { title: 'Lý do', width: 26 },
      { title: 'Tồn', digits: QUANTITY, width: 11 },
      { title: 'ĐVT', width: 8 },
    ],
    rows: report.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.warehouseCode,
        row.lotNo ?? '',
        displayDate(row.expiryDate),
        row.serialNo ?? '',
        qualityLabel(row.qualityStatus),
        row.reasons.map((reason) => REASON_LABELS[reason]).join(', '),
        row.onHand,
        row.unitName,
      ],
    })),
    notes: [generatedNote(report.generatedAt)],
    fileName: fileName('Hang-ngoai-le', report.asOf),
  };
}

export function missingDocumentsReport(report: MissingDocuments): TableReport {
  return {
    title: 'HÀNG THIẾU HỒ SƠ',
    subtitle: `Hàng còn tồn chưa có hồ sơ bắt buộc còn hiệu lực tại ngày ${displayDate(report.asOf)}`,
    columns: [
      { title: 'Mã hàng', width: 14 },
      { title: 'Tên hàng', width: 34 },
      { title: 'Loại hàng', width: 12 },
      { title: 'Loại hồ sơ', width: 30 },
      { title: 'Tình trạng', width: 30 },
      { title: 'Tồn', digits: QUANTITY, width: 11 },
    ],
    rows: report.rows.map((row) => ({
      cells: [
        row.productCode,
        row.productName,
        row.productType,
        `${row.documentTypeCode} - ${row.documentTypeName}`,
        row.expiredDocumentNo
          ? `Hết hạn: ${row.expiredDocumentNo} (${displayDate(row.expiredOn)})`
          : 'Chưa có hồ sơ',
        row.onHand,
      ],
    })),
    notes: [generatedNote(report.generatedAt)],
    fileName: fileName('Hang-thieu-ho-so', report.asOf),
  };
}

const TRACE_COLUMNS: ReportColumn[] = [
  { title: 'Ngày hạch toán', width: 11 },
  { title: 'Số chứng từ', width: 18 },
  { title: 'Nghiệp vụ', width: 20 },
  { title: 'Đối tượng', width: 28 },
  { title: 'Kho', width: 9 },
  { title: 'Mã hàng', width: 13 },
  { title: 'Số lô', width: 12 },
  { title: 'Hạn dùng', width: 11 },
  { title: 'Serial', width: 14 },
  { title: 'Số lượng (+ nhập, − xuất)', digits: QUANTITY, width: 13 },
];

function traceRow(line: TraceLine): ReportRow {
  return {
    cells: [
      displayDate(line.postingDate),
      line.referenceNo,
      `${line.reversal ? 'Đảo: ' : ''}${transactionLabel(line.transactionType)}`,
      [line.partnerCode, line.partnerName].filter(Boolean).join(' - '),
      line.warehouseCode,
      line.productCode,
      line.lotNo ?? '',
      displayDate(line.expiryDate),
      line.serialNo ?? '',
      line.quantity,
    ],
    italic: line.reversal,
    link: { column: 1, to: inventoryDocumentLink(line.referenceId) },
  };
}

export function lotTraceReport(trace: LotTrace): TableReport {
  const subject = trace.serialNo ? `Serial ${trace.serialNo}` : `Lô ${trace.lotNo ?? ''}`;
  return {
    title: 'TRUY XUẤT LÔ / SERIAL',
    subtitle: `${subject}; hàng ${trace.productCode} - ${trace.productName}; hạn dùng ${displayDate(trace.expiryDate)}`,
    columns: TRACE_COLUMNS,
    rows: [
      ...trace.lines.map(traceRow),
      { cells: [null, null, 'Tồn hiện tại', null, null, null, null, null, null, trace.onHand], bold: true },
    ],
    notes: [
      ...trace.customers.map(
        (customer) =>
          `Khách ${[customer.partnerCode, customer.partnerName].filter(Boolean).join(' - ')}: đã xuất ${customer.issued}, trả lại ${customer.returned}, còn giữ ${customer.net} ${trace.unitName}.`,
      ),
      generatedNote(trace.generatedAt),
    ],
    fileName: fileName('Truy-xuat', trace.serialNo ?? trace.lotNo ?? String(trace.productId)),
  };
}

export function customerTraceReport(trace: CustomerTrace): TableReport {
  return {
    title: 'TRUY XUẤT THEO KHÁCH HÀNG',
    subtitle: `Khách ${trace.customerCode} - ${trace.customerName}; ${period(trace.dateFrom, trace.dateTo)}`,
    columns: TRACE_COLUMNS,
    rows: trace.lines.map(traceRow),
    notes: [
      'Số âm: hàng xuất cho khách; số dương: khách trả lại hoặc đảo xuất.',
      generatedNote(trace.generatedAt),
    ],
    fileName: fileName('Truy-xuat-khach', `${trace.customerCode}_${trace.dateFrom}_${trace.dateTo}`),
  };
}
