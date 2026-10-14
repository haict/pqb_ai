import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { pdfBlob } from '../../../shared/pdfmakeLoader';
import type { SalesLedger } from '../types';
import {
  cellText,
  COLUMN_COUNT,
  COST_NOTE,
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
} from './ledgerContent';

const COLUMN_WIDTHS = [44, 44, 58, 44, 44, '*', 34, 30, 40, 52, 60, 40, 52];
const BOLD_KINDS = new Set(['sum', 'net', 'cost', 'profit', 'groupSum', 'total']);

function headerRows(): TableCell[][] {
  const top: TableCell[] = [];
  HEADER_TOP.forEach((item) => {
    top.push({ text: item.label, bold: true, alignment: 'center', colSpan: item.span, rowSpan: item.rows });
    for (let index = 1; index < item.span; index += 1) {
      top.push('');
    }
  });
  const bottom: TableCell[] = [];
  let label = 0;
  HEADER_TOP.forEach((item) => {
    for (let index = 0; index < item.span; index += 1) {
      bottom.push(
        item.rows === 2 ? '' : { text: HEADER_BOTTOM[label++] ?? '', bold: true, alignment: 'center' },
      );
    }
  });
  return [top, bottom];
}

function bodyRows(ledger: SalesLedger): TableCell[][] {
  return ledgerRows(ledger).map((row): TableCell[] => {
    if (row.title !== undefined) {
      return [
        { text: row.title, bold: true, colSpan: COLUMN_COUNT, fillColor: '#f2f2f2' },
        ...Array<TableCell>(COLUMN_COUNT - 1).fill(''),
      ];
    }
    return Array.from({ length: COLUMN_COUNT }, (_, index): TableCell => ({
      text: cellText(row.cells, index),
      alignment: isNumberColumn(index) ? 'right' : 'left',
      bold: BOLD_KINDS.has(row.kind),
      italics: row.line?.reversal ?? false,
    }));
  });
}

function table(ledger: SalesLedger): Content {
  return {
    margin: [0, 6, 0, 0],
    fontSize: 7.5,
    table: { headerRows: 2, widths: COLUMN_WIDTHS, body: [...headerRows(), ...bodyRows(ledger)] },
  };
}

/** Định nghĩa pdfmake (A4 ngang, font Roboto có dấu tiếng Việt); tách riêng để kiểm thử không cần trình duyệt. */
export function ledgerPdfDefinition(ledger: SalesLedger): TDocumentDefinitions {
  return {
    pageSize: 'A4',
    pageOrientation: 'landscape',
    pageMargins: [24, 24, 24, 30],
    info: { title: `${LEDGER_TITLE} ${periodText(ledger)}` },
    defaultStyle: { fontSize: 9, lineHeight: 1.15 },
    footer: (current, total) => ({ text: `Trang ${current}/${total}`, alignment: 'center', fontSize: 8 }),
    content: [
      {
        columns: [
          {
            width: '*',
            stack: [{ text: ledger.companyName ?? '', bold: true }, ledger.companyAddress ?? ''],
          },
          {
            width: 260,
            alignment: 'center',
            stack: [
              { text: FORM_CODE, bold: true },
              { text: FORM_NOTE, italics: true, fontSize: 8 },
            ],
          },
        ],
      },
      { text: LEDGER_TITLE, fontSize: 14, bold: true, alignment: 'center', margin: [0, 10, 0, 0] },
      { text: periodText(ledger), bold: true, italics: true, alignment: 'center' },
      table(ledger),
      ledger.costAvailable ? '' : { text: COST_NOTE, italics: true, fontSize: 8, margin: [0, 2, 0, 0] },
      { text: PAGE_NOTE, margin: [0, 8, 0, 0] },
      OPEN_DATE_NOTE,
      { text: SIGN_DATE, italics: true, alignment: 'right', margin: [0, 6, 0, 2] },
      {
        columns: SIGNERS.map((signer) => ({
          width: '*',
          alignment: 'center',
          stack: [
            { text: signer, bold: true },
            { text: SIGN_HINT, italics: true },
          ],
        })),
      },
    ],
  };
}

export async function ledgerToPdf(ledger: SalesLedger): Promise<Blob> {
  return pdfBlob(ledgerPdfDefinition(ledger));
}
