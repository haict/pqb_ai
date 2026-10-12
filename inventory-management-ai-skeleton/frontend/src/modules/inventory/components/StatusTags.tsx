import { Tag } from 'antd';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import type { QualityStatus } from '../../masterdata/types';
import { STATUS_TAGS } from '../labels';

/** Nhãn trạng thái chứng từ dùng chung cho các phân hệ (kho, công nợ, quỹ, mua hàng). */
export function DocumentStatusTag({ status }: { status: string }) {
  const tag = STATUS_TAGS[status] ?? { label: status, color: 'default' };
  return <Tag color={tag.color}>{tag.label}</Tag>;
}

export function QualityTag({ status }: { status: QualityStatus | null }) {
  if (!status) {
    return null;
  }
  return <Tag color={QUALITY_STATUSES[status].color}>{QUALITY_STATUSES[status].label}</Tag>;
}
