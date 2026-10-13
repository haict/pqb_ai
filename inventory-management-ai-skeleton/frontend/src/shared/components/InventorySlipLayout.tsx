import type { CSSProperties } from 'react';
import { moneyInWords } from '../moneyWords';

const PAGE: CSSProperties = { fontFamily: '"Times New Roman", serif', fontSize: 14, color: '#000' };
const CELL: CSSProperties = { border: '1px solid #000', padding: '2px 4px', verticalAlign: 'top' };
const HEAD: CSSProperties = { ...CELL, textAlign: 'center', fontWeight: 'bold' };
const NUMBER: CSSProperties = { ...CELL, textAlign: 'right', whiteSpace: 'nowrap' };
const COLUMNS = [
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
const COLUMN_KEYS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', '1', '2', '3'];

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

/** Nội dung phiếu nhập / xuất kho theo mẫu doanh nghiệp. */
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

function decimal(value: number, fractionDigits: number) {
  return new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
}

/** "Ngày 07 tháng 10 năm 2026" từ ngày dạng YYYY-MM-DD. */
function longDate(value: string | null) {
  if (!value) {
    return '';
  }
  const [year, month, day] = value.split('-');
  return `Ngày ${day} tháng ${month} năm ${year}`;
}

function Stacked({ values }: { values: string[] }) {
  return (
    <>
      {values.map((value, index) => (
        <div key={index}>{value}</div>
      ))}
    </>
  );
}

/** Bố cục in chung cho phiếu nhập kho, phiếu xuất kho (BR-PUR-08, BR-SAL-21). */
export function InventorySlipLayout({ slip }: { slip: InventorySlipView }) {
  return (
    <div style={PAGE}>
      <div style={{ fontWeight: 'bold' }}>{slip.companyName}</div>
      <div>{slip.companyAddress}</div>
      <div style={{ display: 'flex', alignItems: 'flex-start', marginTop: 12 }}>
        <div style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ fontSize: 22, fontWeight: 'bold' }}>{slip.title}</div>
          <div style={{ fontStyle: 'italic', fontWeight: 'bold' }}>{longDate(slip.slipDate)}</div>
          <div>Số: {slip.slipNo}</div>
        </div>
        <div style={{ width: 140 }}>
          <div>Nợ: {slip.debitAccounts.join(', ')}</div>
          <div>Có: {slip.creditAccounts.join(', ')}</div>
        </div>
      </div>
      <div style={{ marginTop: 12, lineHeight: 1.7 }}>
        {slip.infoRows.map((row) => (
          <div key={row}>- {row}</div>
        ))}
      </div>
      <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 8 }}>
        <thead>
          <tr>
            {COLUMNS.map((column) => (
              <th key={column} style={HEAD}>
                {column}
              </th>
            ))}
          </tr>
          <tr>
            {COLUMN_KEYS.map((key) => (
              <th key={key} style={HEAD}>
                {key}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {slip.lines.map((line) => (
            <tr key={line.lineNo}>
              <td style={{ ...CELL, textAlign: 'center' }}>{line.lineNo}</td>
              <td style={CELL}>{line.productCode}</td>
              <td style={CELL}>
                {line.productName}
                {line.productNote && <div style={{ fontSize: 12 }}>{line.productNote}</div>}
              </td>
              <td style={CELL}>
                <Stacked values={line.lots} />
              </td>
              <td style={CELL}>
                <Stacked values={line.expiries} />
              </td>
              <td style={CELL}>{line.warehouseCode}</td>
              <td style={CELL}>{line.unitName}</td>
              <td style={NUMBER}>{decimal(line.quantity, 2)}</td>
              <td style={NUMBER}>{decimal(line.unitPrice, 2)}</td>
              <td style={NUMBER}>{decimal(line.amount, 0)}</td>
            </tr>
          ))}
          <tr>
            <td style={CELL} />
            <td style={CELL} />
            <td style={{ ...CELL, textAlign: 'center', fontWeight: 'bold' }}>Cộng</td>
            {Array.from({ length: 6 }, (_, index) => (
              <td key={index} style={CELL} />
            ))}
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.totalAmount, 0)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6} />
            <td style={CELL} colSpan={3}>
              Cộng tiền hàng:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.totalAmount, 0)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Thuế suất GTGT:</span>
                <strong>{slip.taxRates.map((rate) => `${decimal(rate, 0)}%`).join(', ')}</strong>
              </div>
            </td>
            <td style={CELL} colSpan={3}>
              Tiền thuế GTGT:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.taxAmount, 0)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6} />
            <td style={CELL} colSpan={3}>
              Tổng tiền thanh toán:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.paymentAmount, 0)}</td>
          </tr>
        </tbody>
      </table>
      <div style={{ marginTop: 12, lineHeight: 1.8 }}>
        <div>
          - Số tiền bằng chữ:{' '}
          <strong style={{ fontStyle: 'italic' }}>{moneyInWords(slip.paymentAmount)}</strong>
        </div>
        <div>- Số chứng từ gốc kèm theo: ..............................................................</div>
      </div>
      <div style={{ textAlign: 'right', fontStyle: 'italic', marginTop: 8 }}>
        Ngày ..... tháng ..... năm ........
      </div>
      <div style={{ display: 'flex', textAlign: 'center', marginTop: 4, minHeight: 120 }}>
        {slip.signers.map((signer) => (
          <div key={signer} style={{ flex: 1 }}>
            <div style={{ fontWeight: 'bold' }}>{signer}</div>
            <div style={{ fontStyle: 'italic' }}>(Ký, họ tên)</div>
          </div>
        ))}
      </div>
    </div>
  );
}
