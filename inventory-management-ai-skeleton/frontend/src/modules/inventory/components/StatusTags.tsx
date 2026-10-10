import { Tag } from 'antd';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import type { QualityStatus } from '../../masterdata/types';
import { STATUS_TAGS } from '../labels';
import type { InventoryDocumentStatus, PolicyStatus, RecallStatus, StockCountStatus } from '../types';

export function DocumentStatusTag({
  status,
}: {
  status: InventoryDocumentStatus | StockCountStatus | RecallStatus | PolicyStatus;
}) {
  const tag = STATUS_TAGS[status] ?? { label: status, color: 'default' };
  return <Tag color={tag.color}>{tag.label}</Tag>;
}

export function QualityTag({ status }: { status: QualityStatus | null }) {
  if (!status) {
    return null;
  }
  return <Tag color={QUALITY_STATUSES[status].color}>{QUALITY_STATUSES[status].label}</Tag>;
}
