import { InputNumber, type InputNumberProps } from 'antd';
import { formatDecimalInput, parseDecimalInput } from '../decimalInput';

type DecimalInputProps = Omit<
  InputNumberProps<number>,
  'precision' | 'formatter' | 'parser' | 'decimalSeparator'
> & {
  /** Số chữ số thập phân tối đa (số lượng 3, đơn giá / tỷ lệ 4 theo D-05). */
  fractionDigits?: number;
};

/** Ô nhập số lượng / tỷ lệ theo định dạng Việt Nam (1,5 là một phẩy năm; 1.000 là một nghìn). */
export function DecimalInput({ fractionDigits = 3, style, ...props }: DecimalInputProps) {
  return (
    <InputNumber<number>
      style={{ width: '100%', ...style }}
      {...props}
      formatter={(value, info) => (info.userTyping ? info.input : formatDecimalInput(value, fractionDigits))}
      // Trả chuỗi (kể cả '' khi xóa trắng) để antd tự đổi sang số hoặc null; kiểu khai báo của antd chỉ nhận số.
      parser={(text) => parseDecimalInput(text, fractionDigits) as unknown as number}
    />
  );
}
