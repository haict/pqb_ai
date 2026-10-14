import ExcelJS from 'exceljs';
import { Packer } from 'docx';
import JSZip from 'jszip';
import { signerSpans, slipFileName, type InventorySlipView } from './slipContent';
import { slipToExcelBuffer } from './slipExcel';
import { slipPdfDefinition } from './slipPdf';
import { slipToWordDocument } from './slipWord';

const slip: InventorySlipView = {
  title: 'PHIẾU NHẬP KHO',
  companyName: 'Công ty mẫu',
  companyAddress: 'Hà Nội',
  slipDate: '2026-10-07',
  slipNo: 'NK/2026/0001',
  debitAccounts: ['1561'],
  creditAccounts: ['331'],
  infoRows: ['Họ và tên người giao hàng: Nhà phân phối', 'Nhập tại kho: Kho tổng'],
  lines: [
    {
      lineNo: 1,
      productCode: 'MAY_01',
      productName: 'Máy xét nghiệm',
      productNote: 'Serial: S1, S2',
      lots: ['L01', 'L02'],
      expiries: ['01/01/2027', '01/02/2027'],
      warehouseCode: 'KHO1',
      unitName: 'Cái',
      quantity: 2,
      unitPrice: 500000,
      amount: 1000000,
    },
  ],
  totalAmount: 1000000,
  taxRates: [10],
  taxAmount: 100000,
  paymentAmount: 1100000,
  signers: ['Người lập phiếu', 'Người giao hàng', 'Thủ kho', 'Kế toán trưởng'],
};

describe('tải phiếu kho', () => {
  it('tên file không dấu, kèm số phiếu', () => {
    expect(slipFileName(slip, 'xlsx')).toBe('Phieu-nhap-kho_NK-2026-0001.xlsx');
    expect(slipFileName({ ...slip, title: 'PHIẾU XUẤT KHO', slipNo: null }, 'pdf')).toBe(
      'Phieu-xuat-kho.pdf',
    );
  });

  it('chia cột chữ ký đều, cột cuối nhận phần dư', () => {
    expect(signerSpans(4, 10)).toEqual([
      [0, 2],
      [2, 2],
      [4, 2],
      [6, 4],
    ]);
  });

  it('Excel: tiêu đề, dòng hàng là ô số, phần thuế và tiền bằng chữ', async () => {
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await slipToExcelBuffer(slip));
    const sheet = workbook.worksheets[0];
    const values: unknown[] = [];
    sheet?.eachRow((row) => row.eachCell((cell) => values.push(cell.value)));

    expect(values).toContain('PHIẾU NHẬP KHO');
    expect(values).toContain('Nợ: 1561');
    expect(values).toContain('Máy xét nghiệm\nSerial: S1, S2');
    expect(values).toContain('L01\nL02');
    expect(values).toContain(1000000);
    expect(values).toContain('Thuế suất GTGT: 10%');
    expect(values).toContain(100000);
    expect(values).toContain(1100000);
    expect(values).toContain('- Số tiền bằng chữ: Một triệu một trăm nghìn đồng chẵn.');
  });

  it('Word: tài liệu chứa tiêu đề, số tiền và người ký', async () => {
    const buffer = await Packer.toBuffer(slipToWordDocument(slip));
    const xml = await (await JSZip.loadAsync(buffer)).file('word/document.xml')?.async('string');

    expect(xml).toContain('PHIẾU NHẬP KHO');
    expect(xml).toContain('1.000.000');
    expect(xml).toContain('Tổng tiền thanh toán:');
    expect(xml).toContain('1.100.000');
    expect(xml).toContain('Kế toán trưởng');
  });

  it('PDF: định nghĩa tài liệu có bảng hàng và phần thuế', () => {
    const text = JSON.stringify(slipPdfDefinition(slip));

    expect(text).toContain('PHIẾU NHẬP KHO');
    expect(text).toContain('Thuế suất GTGT: 10%');
    expect(text).toContain('1.100.000');
    expect(text).toContain('Một triệu một trăm nghìn đồng chẵn.');
  });
});
