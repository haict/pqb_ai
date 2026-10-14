import type { CSSProperties } from 'react';
import { moneyInWords } from '../moneyWords';
import {
  AMOUNT_DIGITS,
  formatDecimal as decimal,
  longDate,
  ORIGINAL_DOCUMENTS_LINE,
  PRICE_DIGITS,
  QUANTITY_DIGITS,
  SIGN_DATE_LINE,
  SIGN_HINT,
  SLIP_COLUMN_KEYS as COLUMN_KEYS,
  SLIP_COLUMNS as COLUMNS,
  taxRatesText,
  type InventorySlipView,
} from '../inventorySlip/slipContent';

export type { InventorySlipView, SlipLineView } from '../inventorySlip/slipContent';

const PAGE: CSSProperties = { fontFamily: '"Times New Roman", serif', fontSize: 14, color: '#000' };
const CELL: CSSProperties = { border: '1px solid #000', padding: '2px 4px', verticalAlign: 'top' };
const HEAD: CSSProperties = { ...CELL, textAlign: 'center', fontWeight: 'bold' };
const NUMBER: CSSProperties = { ...CELL, textAlign: 'right', whiteSpace: 'nowrap' };
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
              <td style={NUMBER}>{decimal(line.quantity, QUANTITY_DIGITS)}</td>
              <td style={NUMBER}>{decimal(line.unitPrice, PRICE_DIGITS)}</td>
              <td style={NUMBER}>{decimal(line.amount, AMOUNT_DIGITS)}</td>
            </tr>
          ))}
          <tr>
            <td style={CELL} />
            <td style={CELL} />
            <td style={{ ...CELL, textAlign: 'center', fontWeight: 'bold' }}>Cộng</td>
            {Array.from({ length: 6 }, (_, index) => (
              <td key={index} style={CELL} />
            ))}
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.totalAmount, AMOUNT_DIGITS)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6} />
            <td style={CELL} colSpan={3}>
              Cộng tiền hàng:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.totalAmount, AMOUNT_DIGITS)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Thuế suất GTGT:</span>
                <strong>{taxRatesText(slip)}</strong>
              </div>
            </td>
            <td style={CELL} colSpan={3}>
              Tiền thuế GTGT:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.taxAmount, AMOUNT_DIGITS)}</td>
          </tr>
          <tr>
            <td style={CELL} colSpan={6} />
            <td style={CELL} colSpan={3}>
              Tổng tiền thanh toán:
            </td>
            <td style={{ ...NUMBER, fontWeight: 'bold' }}>{decimal(slip.paymentAmount, AMOUNT_DIGITS)}</td>
          </tr>
        </tbody>
      </table>
      <div style={{ marginTop: 12, lineHeight: 1.8 }}>
        <div>
          - Số tiền bằng chữ:{' '}
          <strong style={{ fontStyle: 'italic' }}>{moneyInWords(slip.paymentAmount)}</strong>
        </div>
        <div>{ORIGINAL_DOCUMENTS_LINE}</div>
      </div>
      <div style={{ textAlign: 'right', fontStyle: 'italic', marginTop: 8 }}>{SIGN_DATE_LINE}</div>
      <div style={{ display: 'flex', textAlign: 'center', marginTop: 4, minHeight: 120 }}>
        {slip.signers.map((signer) => (
          <div key={signer} style={{ flex: 1 }}>
            <div style={{ fontWeight: 'bold' }}>{signer}</div>
            <div style={{ fontStyle: 'italic' }}>{SIGN_HINT}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
