import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { isNumberColumn, reportCellText, type TableReport } from './tableReport';

const PAGE: CSSProperties = { fontFamily: '"Times New Roman", serif', fontSize: 13, color: '#000' };
const CELL: CSSProperties = { border: '1px solid #000', padding: '2px 4px', verticalAlign: 'top' };
const HEAD: CSSProperties = { ...CELL, textAlign: 'center', fontWeight: 'bold', verticalAlign: 'middle' };
const NUMBER: CSSProperties = { ...CELL, textAlign: 'right', whiteSpace: 'nowrap' };

/** Báo cáo bảng theo bố cục in; dùng trên màn hình (có liên kết chứng từ) và trong hộp xem trước của nút In. */
export function TableReportSheet({ report }: { report: TableReport }) {
  return (
    <div style={PAGE}>
      {report.companyName && <div style={{ fontWeight: 'bold' }}>{report.companyName}</div>}
      {report.companyAddress && <div>{report.companyAddress}</div>}
      <div style={{ textAlign: 'center', margin: '12px 0 8px' }}>
        <div style={{ fontSize: 20, fontWeight: 'bold' }}>{report.title}</div>
        {report.subtitle && <div style={{ fontStyle: 'italic', fontWeight: 'bold' }}>{report.subtitle}</div>}
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              {report.columns.map((column, index) => (
                <th key={index} style={HEAD}>
                  {column.title}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {report.rows.map((row, rowIndex) => (
              <tr
                key={rowIndex}
                style={{
                  fontWeight: row.bold ? 'bold' : undefined,
                  fontStyle: row.italic ? 'italic' : undefined,
                }}
              >
                {report.columns.map((_, index) => (
                  <td key={index} style={isNumberColumn(report, index) ? NUMBER : CELL}>
                    {row.link && row.link.column === index ? (
                      <Link to={row.link.to}>{reportCellText(report, row.cells, index)}</Link>
                    ) : (
                      reportCellText(report, row.cells, index)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {(report.notes ?? []).map((note) => (
        <div key={note} style={{ fontSize: 12, fontStyle: 'italic', marginTop: 4 }}>
          {note}
        </div>
      ))}
    </div>
  );
}
