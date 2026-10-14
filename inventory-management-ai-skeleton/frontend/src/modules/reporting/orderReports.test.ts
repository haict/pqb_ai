import { reportCellText } from '../../shared/report/tableReport';
import {
  orderLink,
  orderProgressReport,
  priceHistoryReport,
  salesJournalReport,
  stockBacklogReport,
  supplierDefectsReport,
} from './orderReports';
import type { OrderProgress, PriceHistory, SalesJournal, StockBacklog, SupplierDefects } from './types';

const generatedAt = '2026-10-14T03:00:00Z';

describe('báo cáo mua / bán đợt 10B', () => {
  it('BR-RPT-26: tình hình đơn bán có giao hàng, trễ hẹn, đã thu, còn phải thu, dòng tổng', () => {
    const progress: OrderProgress = {
      documentType: 'DBH',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      generatedAt,
      rows: [
        {
          documentId: 9,
          documentNo: 'DBH-HN-2026-000009',
          documentDate: '2026-10-05',
          postingDate: '2026-10-05',
          partnerId: 3,
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          status: 'POSTED',
          expectedDate: '2026-10-06',
          deliveryStatus: 'IN_TRANSIT',
          overdueDays: 8,
          totalAmount: 400000,
          returnedAmount: 100000,
          paidAmount: 200000,
          remainingAmount: 100000,
        },
      ],
      total: {
        orderCount: 1,
        overdueCount: 1,
        totalAmount: 400000,
        returnedAmount: 100000,
        paidAmount: 200000,
        remainingAmount: 100000,
      },
    };
    const report = orderProgressReport(progress);
    expect(report.title).toBe('TÌNH HÌNH THỰC HIỆN ĐƠN BÁN');
    const [row, total] = report.rows;
    expect(row?.link).toEqual({ column: 1, to: '/sales/orders?id=9' });
    expect(row && reportCellText(report, row.cells, 3)).toBe('Đã ghi sổ');
    expect(row && reportCellText(report, row.cells, 9)).toBe('100.000');
    expect(total && reportCellText(report, total.cells, 1)).toBe('Cộng: 1 đơn');
    expect(orderProgressReport({ ...progress, documentType: 'DMH' }).columns[9]?.title).toBe('Còn phải trả');
  });

  it('BR-RPT-25: lịch sử giá có dòng tổng hợp theo hàng; hàng lỗi gom theo NCC, không xác định NCC riêng', () => {
    const history: PriceHistory = {
      dateFrom: '2026-01-01',
      dateTo: '2026-10-31',
      generatedAt,
      rows: [
        {
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          postingDate: '2026-10-05',
          documentId: 7,
          documentNo: 'DMH-HN-2026-000007',
          supplierCode: 'NCC01',
          supplierName: 'Nhà phân phối',
          unitName: 'Hộp',
          quantity: 5,
          unitPrice: 500000,
          discountPercent: 0,
          netUnitPrice: 500000,
          baseUnitName: 'Cái',
          netBasePrice: 5000,
        },
      ],
      summaries: [
        {
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          baseUnitName: 'Cái',
          purchaseCount: 1,
          minPrice: 5000,
          maxPrice: 5000,
          lastPrice: 5000,
          lastDate: '2026-10-05',
          lastSupplierName: 'Nhà phân phối',
        },
      ],
    };
    const prices = priceHistoryReport(history);
    expect(prices.rows).toHaveLength(2);
    expect(prices.rows[0]?.link?.to).toBe('/purchasing/orders?id=7');
    expect(prices.rows[1]?.cells[3]).toContain('gần nhất 5.000,00');

    const defects: SupplierDefects = {
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      generatedAt,
      rows: [
        {
          complaintId: 4,
          complaintNo: 'HL-0004',
          receivedDate: '2026-10-05',
          customerCode: 'KH01',
          customerName: 'Bệnh viện A',
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          lotNo: null,
          serialNo: null,
          quantity: 1,
          status: 'OPEN',
          decision: null,
          supplierId: null,
          supplierCode: null,
          supplierName: null,
        },
      ],
      suppliers: [
        {
          supplierId: null,
          supplierCode: null,
          supplierName: null,
          complaintCount: 1,
          productCount: 1,
          quantity: 1,
        },
      ],
    };
    const report = supplierDefectsReport(defects);
    expect(report.rows[0]?.cells[0]).toBe('Không xác định NCC');
    expect(report.rows[1]?.link?.to).toBe('/sales/complaints?id=4');
  });

  it('BR-RPT-26: sổ nhật ký bán hàng có dòng tổng; tồn so với đặt hàng tô đậm dòng thiếu', () => {
    const journal: SalesJournal = {
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      generatedAt,
      rows: [
        {
          postingDate: '2026-10-05',
          documentType: 'TLB',
          documentId: 2,
          documentNo: 'TLB-HN-2026-000002',
          documentDate: '2026-10-05',
          invoiceDate: null,
          invoiceNo: null,
          customerCode: 'KH01',
          customerName: 'Bệnh viện A',
          description: null,
          receivable: -110000,
          goodsAmount: 0,
          serviceAmount: 0,
          deductionAmount: 100000,
          taxAmount: -10000,
          reversal: false,
        },
      ],
      total: {
        receivable: -110000,
        goodsAmount: 0,
        serviceAmount: 0,
        deductionAmount: 100000,
        taxAmount: -10000,
      },
    };
    const report = salesJournalReport(journal);
    expect(report.rows[0]?.cells[5]).toBe('Trả lại');
    expect(report.rows[0]?.link?.to).toBe('/sales/returns?id=2');
    expect(report.rows[1]?.cells[4]).toBe('Tổng cộng');
    expect(orderLink('THN', 3)).toBe('/purchasing/returns?id=3');

    const backlog: StockBacklog = {
      generatedAt,
      rows: [
        {
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          unitName: 'Cái',
          warehouseId: 1,
          warehouseCode: 'KHO_HN',
          ordered: 9,
          orderCount: 1,
          onHand: 7,
          reserved: 0,
          available: 7,
          incoming: 0,
          shortage: 2,
        },
      ],
    };
    expect(stockBacklogReport(backlog).rows[0]?.bold).toBe(true);
  });
});
