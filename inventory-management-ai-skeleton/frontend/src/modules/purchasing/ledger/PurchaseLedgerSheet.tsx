import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { PurchaseLedger } from '../types';
import {
  cellText,
  DOCUMENT_COLUMN,
  isNumberColumn,
  lineCells,
  PURCHASE_COLUMNS,
  PURCHASE_LEDGER_TITLE,
  purchaseDocumentLink,
  subtitle,
  totalCells,
} from './purchaseLedgerContent';

const PAGE: CSSProperties = { fontFamily: '"Times New Roman", serif', fontSize: 13, color: '#000' };
const CELL: CSSProperties = { border: '1px solid #000', padding: '2px 4px', verticalAlign: 'top' };
const HEAD: CSSProperties = { ...CELL, textAlign: 'center', fontWeight: 'bold', verticalAlign: 'middle' };
const NUMBER: CSSProperties = { ...CELL, textAlign: 'right', whiteSpace: 'nowrap' };

/** Sổ chi tiết mua hàng dạng bảng theo màn hình hiện trạng; dùng cho màn hình và bản in. */
export function PurchaseLedgerSheet({ ledger }: { ledger: PurchaseLedger }) {
  const totals = totalCells(ledger);
  return (
    <div style={PAGE}>
      <div style={{ fontWeight: 'bold' }}>{ledger.companyName}</div>
      <div>{ledger.companyAddress}</div>
      <div style={{ textAlign: 'center', margin: '12px 0 8px' }}>
        <div style={{ fontSize: 20, fontWeight: 'bold' }}>{PURCHASE_LEDGER_TITLE}</div>
        <div style={{ fontStyle: 'italic', fontWeight: 'bold' }}>{subtitle(ledger)}</div>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1300 }}>
          <thead>
            <tr>
              {PURCHASE_COLUMNS.map((label) => (
                <th key={label} style={HEAD}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ledger.lines.length === 0 && (
              <tr>
                <td colSpan={PURCHASE_COLUMNS.length} style={{ ...CELL, textAlign: 'center' }}>
                  Không có phát sinh trong kỳ.
                </td>
              </tr>
            )}
            {ledger.lines.map((line, row) => {
              const cells = lineCells(line);
              return (
                <tr key={row} style={line.reversal ? { fontStyle: 'italic' } : undefined}>
                  {PURCHASE_COLUMNS.map((_, index) => (
                    <td key={index} style={isNumberColumn(index) ? NUMBER : CELL}>
                      {index === DOCUMENT_COLUMN ? (
                        <Link to={purchaseDocumentLink(line)}>{line.documentNo}</Link>
                      ) : (
                        cellText(cells, index)
                      )}
                    </td>
                  ))}
                </tr>
              );
            })}
            <tr style={{ fontWeight: 'bold' }}>
              {PURCHASE_COLUMNS.map((_, index) => (
                <td key={index} style={isNumberColumn(index) ? NUMBER : CELL}>
                  {cellText(totals, index)}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
