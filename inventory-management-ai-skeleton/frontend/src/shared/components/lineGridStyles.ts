import type { CSSProperties } from 'react';

/** Cột của bảng dòng chứng từ: nhãn, độ rộng CSS grid, gợi ý (biểu tượng ? cạnh nhãn). */
export interface LineGridColumn {
  title: string;
  width: string;
  tooltip?: string;
}

const ACTION_WIDTH = '32px';
const GAP = 4;

export const lineCellStyle: CSSProperties = { margin: 0 };

export function lineGridStyle(columns: LineGridColumn[]): CSSProperties {
  return {
    display: 'grid',
    gridTemplateColumns: [...columns.map((column) => column.width), ACTION_WIDTH].join(' '),
    gap: GAP,
    alignItems: 'start',
  };
}
