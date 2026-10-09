import { Badge, Tooltip } from 'antd';
import { useSystemHealth } from '../api/useSystemHealth';

export function ServerStatusBadge() {
  const { data, isPending, isError, error } = useSystemHealth();

  if (isPending) {
    return <Badge status="processing" text="Đang kiểm tra máy chủ" />;
  }
  if (isError) {
    return (
      <Tooltip title={error.message}>
        <Badge status="error" text="Mất kết nối máy chủ" />
      </Tooltip>
    );
  }
  if (data.status === 'UP') {
    return <Badge status="success" text="Máy chủ hoạt động" />;
  }
  return <Badge status="warning" text="Máy chủ đang gặp sự cố" />;
}
