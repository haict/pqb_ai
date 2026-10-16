import ExcelJS from 'exceljs';
import { reportCellText } from '../../shared/report/tableReport';
import { tableReportExcelBuffer, tableReportPdfDefinition } from '../../shared/report/tableReportExport';
import { inventoryDocumentLink, lotTraceReport, movementReport, stockCardReport } from './inventoryReports';
import type { InventoryMovement, LotTrace, StockCard } from './types';

const card: StockCard = {
  productId: 5,
  productCode: 'VT01',
  productName: 'Ống nghiệm',
  unitName: 'Cái',
  dateFrom: '2026-10-01',
  dateTo: '2026-10-31',
  opening: 3,
  totalIn: 10,
  totalOut: 4,
  closing: 9,
  generatedAt: '2026-10-14T03:00:00Z',
  lines: [
    {
      postingDate: '2026-10-05',
      transactionType: 'SALES_ISSUE',
      referenceType: 'PXK',
      referenceId: 12,
      referenceNo: 'PXK-HN-2026-000012',
      reversal: false,
      warehouseCode: 'KHO_HN',
      lotNo: 'L01',
      expiryDate: '2028-12-31',
      serialNo: null,
      partnerName: 'Bệnh viện A',
      qualityStatus: 'PASSED',
      inQuantity: null,
      outQuantity: 4,
      balance: -1,
    },
  ],
};

describe('báo cáo kho M10', () => {
  it('BR-RPT-21: thẻ kho có tồn đầu, dòng phát sinh liên kết phiếu kho, dòng cộng có tồn cuối', () => {
    const report = stockCardReport(card);
    expect(report.title).toBe('THẺ KHO');
    expect(report.subtitle).toContain('VT01 - Ống nghiệm');
    expect(report.rows).toHaveLength(3);
    const [opening, line, total] = report.rows;
    expect(opening && reportCellText(report, opening.cells, 10)).toBe('3,00');
    expect(line?.link).toEqual({ column: 1, to: '/inventory/documents/12' });
    expect(line && reportCellText(report, line.cells, 3)).toBe('Bệnh viện A');
    expect(line && reportCellText(report, line.cells, 9)).toBe('4,00');
    expect(total && reportCellText(report, total.cells, 10)).toBe('9,00');
    expect(report.fileName).toBe('The-kho_VT01_2026-10-01_2026-10-31');
    expect(inventoryDocumentLink(7)).toBe('/inventory/documents/7');
  });

  it('BR-RPT-22: nhập - xuất - tồn nhóm theo kho, số lượng + giá trị tạm tính', () => {
    const movement: InventoryMovement = {
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      valueAvailable: false,
      valueProvisional: true,
      generatedAt: '2026-10-14T03:00:00Z',
      rows: [
        {
          productId: 5,
          productCode: 'VT01',
          productName: 'Ống nghiệm',
          unitName: 'Cái',
          warehouseId: 1,
          warehouseCode: 'KHO_HN',
          warehouseName: 'Kho HN',
          opening: 0,
          inQuantity: 11,
          outQuantity: 4,
          closing: 7,
          averageUnitCost: 50000,
          openingValue: 0,
          inValue: 550000,
          outValue: 200000,
          closingValue: 350000,
        },
        {
          productId: 6,
          productCode: 'VT02',
          productName: 'Kim tiêm',
          unitName: 'Hộp',
          warehouseId: 1,
          warehouseCode: 'KHO_HN',
          warehouseName: 'Kho HN',
          opening: 2,
          inQuantity: 0,
          outQuantity: 0,
          closing: 2,
          averageUnitCost: null,
          openingValue: null,
          inValue: null,
          outValue: null,
          closingValue: null,
        },
      ],
    };
    const report = movementReport(movement);
    expect(report.columns).toHaveLength(11);
    expect(report.columns[3]).toMatchObject({ title: 'Số lượng', group: 'Đầu kỳ' });
    expect(report.rows[0]).toMatchObject({ group: true, labelSpan: 3 });
    expect(report.rows[0]?.cells).toEqual([
      'Tên kho : Kho HN (2)',
      null,
      null,
      2,
      0,
      11,
      550000,
      4,
      200000,
      9,
      350000,
    ]);
    expect(report.rows[1]?.cells.slice(3)).toEqual([0, 0, 11, 550000, 4, 200000, 7, 350000]);
    const line = report.rows[1]?.cells ?? [];
    expect(reportCellText(report, line, 3)).toBe('0,00');
    expect(reportCellText(report, line, 6)).toBe('550.000');
    expect(report.notes?.some((note) => note.includes('tạm tính'))).toBe(true);
  });

  it('BR-RPT-24: truy xuất lô ghi tổng theo khách và tồn hiện tại', () => {
    const trace: LotTrace = {
      productId: 5,
      productCode: 'VT01',
      productName: 'Ống nghiệm',
      unitName: 'Cái',
      lotNo: 'L01',
      expiryDate: '2028-12-31',
      serialNo: null,
      lines: [],
      customers: [
        { partnerId: 3, partnerCode: 'KH01', partnerName: 'Bệnh viện A', issued: 4, returned: 1, net: 3 },
      ],
      onHand: 7,
      generatedAt: '2026-10-14T03:00:00Z',
    };
    const report = lotTraceReport(trace);
    expect(report.subtitle).toContain('Lô L01');
    expect(report.notes?.[0]).toBe('Khách KH01 - Bệnh viện A: đã xuất 4, trả lại 1, còn giữ 3 Cái.');
  });

  it('bảng báo cáo dùng chung: Excel giữ ô số, PDF khổ ngang', async () => {
    const report = stockCardReport(card);
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await tableReportExcelBuffer(report));
    const values: unknown[] = [];
    workbook.worksheets[0]?.eachRow((row) => row.eachCell((cell) => values.push(cell.value)));
    expect(values).toContain('THẺ KHO');
    expect(values).toContain('PXK-HN-2026-000012');
    expect(values).toContain(9);
    expect(tableReportPdfDefinition(report).pageOrientation).toBe('landscape');
  });
});
