import { Col, type ColProps } from 'antd';
import { BRANCH_VISIBLE } from '../branch';

/** Cột chứa ô chi nhánh của form: khi ẩn vẫn giữ Form.Item (giữ giá trị, gửi lên server) nhưng không chiếm chỗ. */
export function BranchCol({ children, ...colProps }: ColProps) {
  if (BRANCH_VISIBLE) {
    return <Col {...colProps}>{children}</Col>;
  }
  return <div style={{ display: 'none' }}>{children}</div>;
}
