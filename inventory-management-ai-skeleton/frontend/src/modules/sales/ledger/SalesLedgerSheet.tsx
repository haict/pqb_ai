import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { SalesLedger } from '../types';
import {
  cellText,
  COLUMN_COUNT,
  COST_NOTE,
  documentLink,
  FORM_CODE,
  FORM_NOTE,
  HEADER_BOTTOM,
  HEADER_TOP,
  isNumberColumn,
  LEDGER_TITLE,
  ledgerRows,
  OPEN_DATE_NOTE,
  PAGE_NOTE,
  periodText,
  SIGN_DATE,
  SIGN_HINT,
  SIGNERS,
  type LedgerRow,
} from './ledgerContent';

const PAGE: CSSProperties = { fontFamily: '"Times New Roman", serif', fontSize: 13, color: '#000' };
const CELL: CSSProperties = { border: '1px solid #000', padding: '2px 4px', verticalAlign: 'top' };
const HEAD: CSSProperties = { ...CELL, textAlign: 'center', fontWeight: 'bold', verticalAlign: 'middle' };
const NUMBER: CSSProperties = { ...CELL, textAlign: 'right', whiteSpace: 'nowrap' };
const DOCUMENT_COLUMN = 2;
const BOLD_KINDS = new Set(['sum', 'net', 'cost', 'profit', 'groupSum', 'total']);

function Row({ row }: { row: LedgerRow }) {
  if (row.title !== undefined) {
    return (
      <tr>
        <td colSpan={COLUMN_COUNT} style={{ ...CELL, fontWeight: 'bold', background: '#f5f5f5' }}>
          {row.title}
        </td>
      </tr>
    );
  }
  const bold = BOLD_KINDS.has(row.kind);
  return (
    <tr style={row.line?.reversal ? { fontStyle: 'italic' } : undefined}>
      {Array.from({ length: COLUMN_COUNT }, (_, index) => {
        const text = cellText(row.cells, index);
        const style: CSSProperties = {
          ...(isNumberColumn(index) ? NUMBER : CELL),
          fontWeight: bold ? 'bold' : undefined,
        };
        return (
          <td key={index} style={style}>
            {index === DOCUMENT_COLUMN && row.line ? <Link to={documentLink(row.line)}>{text}</Link> : text}
          </td>
        );
      })}
    </tr>
  );
}

/** Sổ chi tiết bán hàng theo bố cục mẫu S16-DNN; dùng cho màn hình và bản in. */
export function SalesLedgerSheet({ ledger }: { ledger: SalesLedger }) {
  const rows = ledgerRows(ledger);
  return (
    <div style={PAGE}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
        <div>
          <div style={{ fontWeight: 'bold' }}>{ledger.companyName}</div>
          <div>{ledger.companyAddress}</div>
        </div>
        <div style={{ textAlign: 'center', maxWidth: 320 }}>
          <div style={{ fontWeight: 'bold' }}>{FORM_CODE}</div>
          <div style={{ fontStyle: 'italic', fontSize: 12 }}>{FORM_NOTE}</div>
        </div>
      </div>
      <div style={{ textAlign: 'center', margin: '12px 0 8px' }}>
        <div style={{ fontSize: 20, fontWeight: 'bold' }}>{LEDGER_TITLE}</div>
        <div style={{ fontStyle: 'italic', fontWeight: 'bold' }}>{periodText(ledger)}</div>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1100 }}>
          <thead>
            <tr>
              {HEADER_TOP.map((header) => (
                <th key={header.label} style={HEAD} colSpan={header.span} rowSpan={header.rows}>
                  {header.label}
                </th>
              ))}
            </tr>
            <tr>
              {HEADER_BOTTOM.map((label, index) => (
                <th key={`${label}-${index}`} style={HEAD}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={COLUMN_COUNT} style={{ ...CELL, textAlign: 'center' }}>
                  Không có phát sinh trong kỳ.
                </td>
              </tr>
            ) : (
              rows.map((row, index) => <Row key={index} row={row} />)
            )}
          </tbody>
        </table>
      </div>
      {!ledger.costAvailable && (
        <div style={{ fontSize: 12, fontStyle: 'italic', marginTop: 4 }}>{COST_NOTE}</div>
      )}
      <div style={{ marginTop: 8, lineHeight: 1.7 }}>
        <div>{PAGE_NOTE}</div>
        <div>{OPEN_DATE_NOTE}</div>
      </div>
      <div style={{ textAlign: 'right', fontStyle: 'italic', marginTop: 8 }}>{SIGN_DATE}</div>
      <div style={{ display: 'flex', marginTop: 4 }}>
        {SIGNERS.map((signer) => (
          <div key={signer} style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ fontWeight: 'bold' }}>{signer}</div>
            <div style={{ fontStyle: 'italic' }}>{SIGN_HINT}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
