import { Empty, Space, Steps, Tag, Timeline, Typography } from 'antd';
import { formatDateTime } from '../../../shared/format';
import type { ApprovalActionType, ApprovalRequest, ApprovalRequestStatus } from '../types';

const STATUS: Record<ApprovalRequestStatus, { label: string; color: string }> = {
  PENDING: { label: 'Chờ duyệt', color: 'gold' },
  APPROVED: { label: 'Đã duyệt', color: 'green' },
  REJECTED: { label: 'Bị từ chối', color: 'red' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
};

const ACTIONS: Record<ApprovalActionType, { label: string; color: string }> = {
  SUBMIT: { label: 'Gửi duyệt', color: 'blue' },
  APPROVE: { label: 'Duyệt', color: 'green' },
  REJECT: { label: 'Từ chối', color: 'red' },
  CANCEL: { label: 'Hủy yêu cầu', color: 'gray' },
};

export function ApprovalStatusTag({ status }: { status: ApprovalRequestStatus }) {
  return <Tag color={STATUS[status].color}>{STATUS[status].label}</Tag>;
}

function stepStatus(request: ApprovalRequest, index: number) {
  const position = index + 1;
  if (request.status === 'APPROVED' || position < request.currentStepPosition) {
    return 'finish' as const;
  }
  if (position === request.currentStepPosition) {
    return request.status === 'REJECTED'
      ? ('error' as const)
      : request.status === 'PENDING'
        ? ('process' as const)
        : ('wait' as const);
  }
  return 'wait' as const;
}

/** Các bước duyệt và lịch sử xử lý của một hoặc nhiều lần gửi duyệt (mới nhất trước). */
export function ApprovalHistory({ requests }: { requests: ApprovalRequest[] }) {
  if (requests.length === 0) {
    return <Empty description="Chưa gửi duyệt" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {requests.map((request) => (
        <div key={request.id}>
          <Space style={{ marginBottom: 12 }}>
            <Typography.Text strong>Lần gửi duyệt lúc {formatDateTime(request.requestedAt)}</Typography.Text>
            <ApprovalStatusTag status={request.status} />
          </Space>
          <Steps
            size="small"
            style={{ marginBottom: 16 }}
            items={request.steps.map((step, index) => ({
              title: `Bước ${index + 1}`,
              description:
                step.approverType === 'ROLE' ? `Vai trò: ${step.roleName ?? ''}` : (step.userName ?? ''),
              status: stepStatus(request, index),
            }))}
          />
          <Timeline
            items={request.actions.map((action) => ({
              color: ACTIONS[action.action].color,
              children: (
                <>
                  <Typography.Text strong>{ACTIONS[action.action].label}</Typography.Text>
                  {' - '}
                  {action.actorName}
                  {action.onBehalfOfName && ` (thay ${action.onBehalfOfName})`}
                  {' - '}
                  <Typography.Text type="secondary">{formatDateTime(action.actedAt)}</Typography.Text>
                  {action.comment && <div>{action.comment}</div>}
                </>
              ),
            }))}
          />
        </div>
      ))}
    </Space>
  );
}
