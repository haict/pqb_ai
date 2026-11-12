import { fireEvent, render, screen } from '@testing-library/react';
import { DatePicker } from 'antd';
import type { Dayjs } from 'dayjs';
import { DATE_INPUT_FORMAT } from './dateInput';

function typeDate(text: string): Dayjs | null | undefined {
  const onChange = vi.fn();
  render(<DatePicker format={DATE_INPUT_FORMAT} placeholder="Ngày" onChange={onChange} />);
  const input = screen.getByPlaceholderText('Ngày');
  fireEvent.mouseDown(input);
  fireEvent.focus(input);
  fireEvent.change(input, { target: { value: text } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
  return onChange.mock.calls.at(-1)?.[0] as Dayjs | null | undefined;
}

describe('DATE_INPUT_FORMAT', () => {
  it.each([
    ['15012025', '2025-01-15'],
    ['15/01/2025', '2025-01-15'],
    ['5/1/2025', '2025-01-05'],
    ['15-01-2025', '2025-01-15'],
    ['15.01.2025', '2025-01-15'],
  ])('gõ %s hiểu là %s', (text, expected) => {
    expect(typeDate(text)?.format('YYYY-MM-DD')).toBe(expected);
  });

  it('gõ từng phím 12012020 ra 12/01/2020, không bị định dạng lại giữa chừng', () => {
    const onChange = vi.fn();
    render(<DatePicker format={DATE_INPUT_FORMAT} placeholder="Ngày gõ phím" onChange={onChange} />);
    const input = screen.getByPlaceholderText<HTMLInputElement>('Ngày gõ phím');
    fireEvent.mouseDown(input);
    fireEvent.focus(input);
    for (const ch of '12012020') {
      // Như trình duyệt: phím mới nối vào nội dung đang hiển thị của ô.
      fireEvent.change(input, { target: { value: input.value + ch } });
    }
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
    const picked = onChange.mock.calls.at(-1)?.[0] as Dayjs | undefined;
    expect(picked?.format('YYYY-MM-DD')).toBe('2020-01-12');
  });

  it('ngày không hợp lệ không được chọn', () => {
    expect(typeDate('32132025')).toBeUndefined();
  });

  it('hiển thị theo DD/MM/YYYY', () => {
    expect(DATE_INPUT_FORMAT[0]).toBe('DD/MM/YYYY');
  });
});
