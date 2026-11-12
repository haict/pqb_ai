import type { CSSProperties } from 'react';

/** Cột của bảng dòng chứng từ: nhãn, độ rộng CSS grid, gợi ý (biểu tượng ? cạnh nhãn). */
export interface LineGridColumn {
  title: string;
  width: string;
  tooltip?: string;
}

const ACTION_WIDTH = '32px';
const GAP = 4;
/** Cột số thứ tự đầu bảng, do giao diện tự đếm (không lưu, không gửi server). */
export const LINE_NO_WIDTH = 36;
/** Chiều rộng thêm vào bảng do cột số thứ tự. */
export const LINE_NO_EXTRA = LINE_NO_WIDTH + GAP;

export const lineCellStyle: CSSProperties = { margin: 0 };

export function lineGridStyle(columns: LineGridColumn[]): CSSProperties {
  return {
    display: 'grid',
    gridTemplateColumns: [`${LINE_NO_WIDTH}px`, ...columns.map((column) => column.width), ACTION_WIDTH].join(' '),
    gap: GAP,
    alignItems: 'start',
  };
}
