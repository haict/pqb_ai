import { Card, Descriptions, Typography } from 'antd';
import { ServerStatusBadge } from '../../shared/components/ServerStatusBadge';

export function HomePage() {
  return (
    <>
      <Typography.Title level={3}>Trang chủ</Typography.Title>
      <Card title="Trạng thái hệ thống" style={{ maxWidth: 640 }}>
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Máy chủ ứng dụng">
            <ServerStatusBadge />
          </Descriptions.Item>
        </Descriptions>
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
          Các phân hệ nghiệp vụ sẽ xuất hiện trên thanh bên khi được đưa vào sử dụng.
        </Typography.Paragraph>
      </Card>
    </>
  );
}
