import dayjs, { type Dayjs } from 'dayjs';
import type { DocumentNumberFormat } from './types';

/** Cấu hình đặt trước khi loại chứng từ được cấp số lần đầu có hiệu lực từ ngày này (áp dụng cho mọi ngày). */
export const FROM_BEGINNING = '2000-01-01';

/** Ví dụ số chứng từ, ví dụ PXK-HN-2026-000001 (D-03). */
export function sampleNumber(
  format: Pick<DocumentNumberFormat, 'prefix' | 'sequenceLength' | 'resetCycle' | 'effectiveFrom'>,
  branchCode: string,
  today: Dayjs = dayjs(),
): string {
  const effective = dayjs(format.effectiveFrom);
  const date = effective.isAfter(today) ? effective : today;
  const period = format.resetCycle === 'MONTHLY' ? date.format('YYYYMM') : date.format('YYYY');
  return `${format.prefix}-${branchCode}-${period}-${'1'.padStart(format.sequenceLength, '0')}`;
}
