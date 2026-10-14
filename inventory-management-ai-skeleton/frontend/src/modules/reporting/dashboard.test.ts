import { reportCellText } from '../../shared/report/tableReport';
import {
  backlogDetailsReport,
  debtDetailsReport,
  debtLink,
  expiryDetailsReport,
  tradeDetailsReport,
} from './dashboard';
import type { BacklogDetails, DebtDetails, ExpiryDetails, TradeDetails } from './types';

const generatedAt = '2026-10-14T03:00:00Z';

describe('dashboard điều hành đợt 10C', () => {
  it('BR-RPT-01, 11: chi tiết doanh thu có trả lại âm, dòng đảo nghiêng, mở chứng từ, dòng tổng', () => {
    const details: TradeDetails = {
      kind: 'revenue',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      rows: [
        {
          entryDate: '2026-10-05',
          documentType: 'DBH',
          documentId: 9,
          documentNo: 'DBH-HN-2026-000009',
          reversal: false,
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          amount: 400000,
        },
        {
          entryDate: '2026-10-06',
          documentType: 'TLB',
          documentId: 2,
          documentNo: 'TLB-HN-2026-000002',
          reversal: true,
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          amount: 100000,
        },
      ],
      total: 500000,
      generatedAt,
    };
    const report = tradeDetailsReport(details, 'Khách KH01 - Bệnh viện A');
    expect(report.title).toBe('CHI TIẾT DOANH THU THUẦN');
    expect(report.subtitle).toContain('Khách KH01');
    expect(report.rows[0]?.link).toEqual({ column: 2, to: '/sales/orders?id=9' });
    expect(report.rows[1]?.cells[1]).toBe('Trả lại (đảo)');
    expect(report.rows[1]?.italic).toBe(true);
    const total = report.rows[2];
    expect(total && reportCellText(report, total.cells, 4)).toBe('500.000');
    expect(tradeDetailsReport({ ...details, kind: 'purchases' }).columns[3]?.title).toBe('Nhà cung cấp');
  });

  it('BR-RPT-02: công nợ in đậm khoản quá hạn, chỉ mở được đơn mua / bán', () => {
    const details: DebtDetails = {
      debtType: 'RECEIVABLE',
      today: '2026-10-14',
      rows: [
        {
          debtItemId: 1,
          sourceType: 'DBH',
          sourceId: 9,
          sourceNo: 'DBH-HN-2026-000009',
          invoiceNo: null,
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          documentDate: '2026-09-01',
          dueDate: '2026-10-01',
          originalAmount: 400000,
          settledAmount: 100000,
          remainingAmount: 300000,
          overdueDays: 13,
        },
        {
          debtItemId: 2,
          sourceType: 'CNDK',
          sourceId: 4,
          sourceNo: 'CNDK-0004',
          invoiceNo: 'HD01',
          partnerCode: 'KH02',
          partnerName: 'Phòng khám B',
          documentDate: '2026-10-01',
          dueDate: '2026-11-01',
          originalAmount: 50000,
          settledAmount: 0,
          remainingAmount: 50000,
          overdueDays: 0,
        },
      ],
      totalAmount: 350000,
      overdueAmount: 300000,
      generatedAt,
    };
    const report = debtDetailsReport(details);
    expect(report.rows[0]?.bold).toBe(true);
    expect(report.rows[0]?.link?.to).toBe('/sales/orders?id=9');
    expect(report.rows[1]?.link).toBeUndefined();
    expect(report.rows[1]?.cells[5]).toBeNull();
    expect(report.rows[2]?.cells[2]).toBe('Tổng cộng (quá hạn 300.000)');
    expect(debtLink('DMH', 3)).toBe('/purchasing/orders?id=3');
    expect(debtDetailsReport({ ...details, debtType: 'PAYABLE' }).title).toBe('CÔNG NỢ PHẢI TRẢ CÒN LẠI');
  });

  it('BR-RPT-04, 05, 06: lô quá hạn in đậm; đơn bán có cột giao hàng, đơn mua không', () => {
    const expiry: ExpiryDetails = {
      warningDays: 90,
      today: '2026-10-14',
      warningUntil: '2027-01-12',
      rows: [
        {
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          unitName: 'Cái',
          warehouseCode: 'KHO_HN',
          lotId: 7,
          lotNo: 'L01',
          expiryDate: '2026-10-10',
          daysLeft: -4,
          onHand: 7,
        },
      ],
      generatedAt,
    };
    expect(expiryDetailsReport(expiry).rows[0]?.bold).toBe(true);

    const backlog: BacklogDetails = {
      documentType: 'DBH',
      today: '2026-10-14',
      rows: [
        {
          documentId: 9,
          documentNo: 'DBH-HN-2026-000009',
          documentDate: '2026-10-05',
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          status: 'POSTED',
          deliveryStatus: 'IN_TRANSIT',
          expectedDate: '2026-10-06',
          overdueDays: 8,
          totalAmount: 400000,
        },
      ],
      totalAmount: 400000,
      generatedAt,
    };
    const sales = backlogDetailsReport(backlog);
    expect(sales.columns.map((column) => column.title)).toContain('Giao hàng');
    expect(sales.rows[0]?.link?.to).toBe('/sales/orders?id=9');
    expect(sales.rows[1]?.cells[2]).toBe('Cộng: 1 đơn');
    const purchases = backlogDetailsReport({ ...backlog, documentType: 'DMH' });
    expect(purchases.title).toBe('ĐƠN MUA CHƯA NHẬN');
    expect(purchases.columns).toHaveLength(7);
    expect(purchases.rows[0]?.cells).toHaveLength(7);
  });
});
