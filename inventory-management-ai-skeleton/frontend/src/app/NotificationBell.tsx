import { BellOutlined } from '@ant-design/icons';
import { Badge, Button, Empty, List, Popover, Space, Typography } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { notificationApi } from '../modules/system/api';
import type { AppNotification } from '../modules/system/types';
import { formatDateTime } from '../shared/format';
import { documentLink } from './documentLinks';

const POLL_INTERVAL_MS = 60_000;

/** Chuông thông báo trên thanh tiêu đề (BR-SYS-25, BR-UI): số chưa đọc, 10 thông báo mới nhất. */
export function NotificationBell() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.unreadCount,
    refetchInterval: POLL_INTERVAL_MS,
  });
  const latest = useQuery({
    queryKey: ['notifications', 'latest'],
    queryFn: () => notificationApi.list({ page: 0, size: 10 }),
    enabled: open,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

  const openNotification = async (notification: AppNotification) => {
    if (!notification.readAt) {
      await notificationApi.markRead(notification.id);
      await refresh();
    }
    const link = documentLink(notification.linkType, notification.linkId);
    setOpen(false);
    navigate(link ?? '/approvals');
  };

  const content = (
    <div style={{ width: 360, maxWidth: '80vw' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
        <Typography.Text strong>Thông báo</Typography.Text>
        <Button
          size="small"
          type="link"
          disabled={!unread.data}
          onClick={async () => {
            await notificationApi.markAllRead();
            await refresh();
          }}
        >
          Đánh dấu tất cả đã đọc
        </Button>
      </Space>
      <List
        size="small"
        loading={latest.isFetching}
        dataSource={latest.data?.items ?? []}
        locale={{
          emptyText: <Empty description="Không có thông báo" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        renderItem={(notification) => (
          <List.Item
            style={{ cursor: 'pointer', background: notification.readAt ? undefined : '#e6f4ff' }}
            onClick={() => openNotification(notification)}
          >
            <List.Item.Meta
              title={notification.title}
              description={
                <>
                  {notification.message && <div>{notification.message}</div>}
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {formatDateTime(notification.createdAt)}
                  </Typography.Text>
                </>
              }
            />
          </List.Item>
        )}
      />
    </div>
  );

  return (
    <Popover content={content} trigger="click" open={open} onOpenChange={setOpen} placement="bottomRight">
      <Badge count={unread.data ?? 0} size="small">
        <Button className="app-header-icon" aria-label="Thông báo" icon={<BellOutlined />} />
      </Badge>
    </Popover>
  );
}
