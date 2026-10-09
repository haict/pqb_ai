import { Tag } from 'antd';

export type RecordStatus = 'ACTIVE' | 'INACTIVE';

export function StatusTag({ status }: { status: RecordStatus }) {
  return status === 'ACTIVE' ? <Tag color="green">Hoạt động</Tag> : <Tag>Ngừng hoạt động</Tag>;
}
