/**
 * Định dạng ô nhập ngày: định dạng đầu dùng để hiển thị, các định dạng sau để hiểu ngày gõ tay không dấu phân cách
 * (15012025) hoặc dùng dấu khác (15-01-2025, 15.01.2025, 5/1/2025).
 *
 * Không dùng 'DDMMYY': ô ngày phân tích sau mỗi phím gõ, nên 6 số đầu của '12012020' khớp ngay thành 12/01/2020,
 * ô tự định dạng lại và các phím gõ tiếp làm sai ngày.
 */
export const DATE_INPUT_FORMAT: string[] = [
  'DD/MM/YYYY',
  'DDMMYYYY',
  'D/M/YYYY',
  'DD-MM-YYYY',
  'D-M-YYYY',
  'DD.MM.YYYY',
  'D.M.YYYY',
];
