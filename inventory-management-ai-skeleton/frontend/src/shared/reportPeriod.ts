import dayjs, { type Dayjs } from 'dayjs';

/** Kỳ báo cáo dùng chung cho các sổ chi tiết (màn hình tham số hiện trạng: "Đầu tháng đến hiện tại"...). */

export function displayDate(value: string | null | undefined) {
  return value ? dayjs(value).format('DD/MM/YYYY') : '';
}

export type PeriodPreset =
  'MONTH_TO_DATE' | 'THIS_MONTH' | 'LAST_MONTH' | 'THIS_QUARTER' | 'YEAR_TO_DATE' | 'THIS_YEAR' | 'CUSTOM';

export const PERIOD_PRESETS: Record<PeriodPreset, string> = {
  MONTH_TO_DATE: 'Đầu tháng đến hiện tại',
  THIS_MONTH: 'Tháng này',
  LAST_MONTH: 'Tháng trước',
  THIS_QUARTER: 'Quý này',
  YEAR_TO_DATE: 'Đầu năm đến hiện tại',
  THIS_YEAR: 'Năm nay',
  CUSTOM: 'Tùy chọn',
};

/** Khoảng ngày của kỳ báo cáo tính từ ngày {@code today}; "Tùy chọn" giữ nguyên khoảng đang chọn. */
export function periodRange(preset: PeriodPreset, today: Dayjs, current: [Dayjs, Dayjs]): [Dayjs, Dayjs] {
  switch (preset) {
    case 'MONTH_TO_DATE':
      return [today.startOf('month'), today];
    case 'THIS_MONTH':
      return [today.startOf('month'), today.endOf('month')];
    case 'LAST_MONTH': {
      const last = today.subtract(1, 'month');
      return [last.startOf('month'), last.endOf('month')];
    }
    case 'THIS_QUARTER': {
      const start = today.startOf('month').month(Math.floor(today.month() / 3) * 3);
      return [start, start.add(2, 'month').endOf('month')];
    }
    case 'YEAR_TO_DATE':
      return [today.startOf('year'), today];
    case 'THIS_YEAR':
      return [today.startOf('year'), today.endOf('year')];
    case 'CUSTOM':
      return current;
  }
}

/** Giá trị form của kỳ báo cáo: mẫu kỳ và khoảng ngày. */
export interface PeriodValues {
  preset: PeriodPreset;
  range: [Dayjs, Dayjs];
}

export function initialPeriod(preset: PeriodPreset = 'MONTH_TO_DATE'): PeriodValues {
  const today = dayjs();
  return { preset, range: periodRange(preset, today, [today, today]) };
}

export function rangeParams(range: [Dayjs, Dayjs]) {
  return { dateFrom: range[0].format('YYYY-MM-DD'), dateTo: range[1].format('YYYY-MM-DD') };
}
