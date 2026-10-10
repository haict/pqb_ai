import dayjs from 'dayjs';
import { FROM_BEGINNING, sampleNumber } from './documentNumber';

const today = dayjs('2026-10-15');

describe('sampleNumber', () => {
  it('D-03: đặt lại theo năm in YYYY', () => {
    expect(
      sampleNumber(
        { prefix: 'PXK', sequenceLength: 6, resetCycle: 'YEARLY', effectiveFrom: FROM_BEGINNING },
        'HN',
        today,
      ),
    ).toBe('PXK-HN-2026-000001');
  });

  it('đặt lại theo tháng in YYYYMM; cấu hình đặt trước lấy kỳ của ngày hiệu lực', () => {
    expect(
      sampleNumber(
        { prefix: 'XK', sequenceLength: 4, resetCycle: 'MONTHLY', effectiveFrom: '2027-01-01' },
        'HCM',
        today,
      ),
    ).toBe('XK-HCM-202701-0001');
  });
});
