import { Card, DatePicker, Input, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { formatDateTime } from '../../../shared/format';
import { loginLogApi } from '../api';
import type { LoginEvent, LoginLog } from '../types';

const EVENTS: Record<LoginEvent, { label: string; color: string }> = {
  LOGIN_SUCCESS: { label: 'Đăng nhập', color: 'green' },
  LOGIN_FAILED: { label: 'Đăng nhập sai', color: 'orange' },
  ACCOUNT_LOCKED: { label: 'Khóa tạm tài khoản', color: 'red' },
  LOGOUT: { label: 'Đăng xuất', color: 'default' },
  REFRESH_REUSE_DETECTED: { label: 'Nghi phiên bị đánh cắp', color: 'magenta' },
  PASSWORD_CHANGED: { label: 'Đổi mật khẩu', color: 'blue' },
};

/** Nhật ký đăng nhập (BR-SYS-20), chỉ đọc. */
export function LoginLogsPage() {
  const [username, setUsername] = useState('');
  const [event, setEvent] = useState<LoginEvent>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const { tableProps } = useServerTable('login-logs', loginLogApi.list, {
    username,
    event,
    from: range?.[0]?.startOf('day').toISOString(),
    to: range?.[1]?.add(1, 'day').startOf('day').toISOString(),
  });

  const columns: TableColumnsType<LoginLog> = [
    {
      title: 'Thời điểm',
      dataIndex: 'occurredAt',
      sorter: true,
      defaultSortOrder: 'descend',
      width: 160,
      render: formatDateTime,
    },
    {
      title: 'Sự kiện',
      dataIndex: 'event',
      width: 200,
      render: (value: LoginEvent) => <Tag color={EVENTS[value].color}>{EVENTS[value].label}</Tag>,
    },
    { title: 'Tên đăng nhập', dataIndex: 'username', width: 180 },
    { title: 'Địa chỉ IP', dataIndex: 'ipAddress', width: 140 },
    { title: 'Trình duyệt', dataIndex: 'userAgent', ellipsis: true },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Nhật ký đăng nhập
      </Typography.Title>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search placeholder="Tên đăng nhập" allowClear onSearch={setUsername} style={{ width: 220 }} />
        <Select
          placeholder="Sự kiện"
          allowClear
          value={event}
          onChange={setEvent}
          style={{ width: 220 }}
          options={Object.entries(EVENTS).map(([value, { label }]) => ({ value, label }))}
        />
        <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(value) => setRange(value)} />
      </Space>
      <Table<LoginLog> {...tableProps} columns={columns} scroll={{ x: 900 }} />
    </Card>
  );
}
