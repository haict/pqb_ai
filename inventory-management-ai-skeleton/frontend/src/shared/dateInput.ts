/**
 * Định dạng ô nhập ngày: định dạng đầu dùng để hiển thị, các định dạng sau để hiểu ngày gõ tay không dấu phân cách
 * (15012025, 150125) hoặc dùng dấu khác (15-01-2025, 15.01.2025, 5/1/2025).
 */
export const DATE_INPUT_FORMAT: string[] = [
  'DD/MM/YYYY',
  'DDMMYYYY',
  'DDMMYY',
  'D/M/YYYY',
  'DD-MM-YYYY',
  'D-M-YYYY',
  'DD.MM.YYYY',
  'D.M.YYYY',
];
