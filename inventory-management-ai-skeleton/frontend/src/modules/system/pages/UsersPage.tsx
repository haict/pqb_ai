import { DownOutlined, EditOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Checkbox,
  Dropdown,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type MenuProps,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useAuth, useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { formatDateTime } from '../../../shared/format';
import { formValues } from '../../../shared/forms';
import { userApi, type CreateUserInput, type UpdateUserInput } from '../api';
import { DataScopeFields } from '../components/DataScopeFields';
import { useActiveRoles, useCompanies, useMyBranches } from '../lookups';
import type { DataScope, UserAccount } from '../types';

type Dialog =
  | { kind: 'create' }
  | { kind: 'edit'; user: UserAccount }
  | { kind: 'roles'; user: UserAccount }
  | { kind: 'scopes'; user: UserAccount }
  | { kind: 'password'; user: UserAccount };

const PASSWORD_RULES = [
  { required: true, message: 'Nhập mật khẩu' },
  { min: 8, message: 'Tối thiểu 8 ký tự' },
];

export function UsersPage() {
  const can = useCan();
  const { user: me } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companies = useCompanies();
  const branches = useMyBranches();
  const roles = useActiveRoles();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const { tableProps } = useServerTable('users', userApi.list, { keyword, status });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] });
  const statusChange = useStatusChange(
    userApi.changeStatus,
    (item) => `${item.username} - ${item.fullName}`,
    refresh,
  );
  const roleOptions = roles.map((role) => ({ value: role.id, label: role.name }));
  const close = () => setDialog(null);

  const actionsOf = (item: UserAccount): MenuProps['items'] => {
    const self = item.id === me?.id;
    return [
      can('user:assign-roles') && !self && { key: 'roles', label: 'Gán vai trò' },
      can('user:assign-scopes') && !self && { key: 'scopes', label: 'Phạm vi dữ liệu' },
      can('user:reset-password') && !self && { key: 'password', label: 'Đặt lại mật khẩu' },
      can('user:update') &&
        !self && {
          key: 'status',
          label: item.status === 'ACTIVE' ? 'Ngừng hoạt động' : 'Kích hoạt lại',
          danger: item.status === 'ACTIVE',
        },
    ].filter(Boolean) as MenuProps['items'];
  };

  const onAction = (item: UserAccount, key: string) => {
    if (key === 'status') {
      statusChange.open(item);
    } else {
      setDialog({ kind: key as 'roles' | 'scopes' | 'password', user: item });
    }
  };

  const columns: TableColumnsType<UserAccount> = [
    { title: 'Tên đăng nhập', dataIndex: 'username', sorter: true, width: 150 },
    { title: 'Họ tên', dataIndex: 'fullName', sorter: true },
    {
      title: 'Chi nhánh mặc định',
      dataIndex: 'defaultBranchId',
      render: (id: number | null) => branches.nameOf(id),
    },
    {
      title: 'Vai trò',
      dataIndex: 'roles',
      render: (value: UserAccount['roles']) => value.map((role) => <Tag key={role.id}>{role.name}</Tag>),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 190,
      render: (value: RecordStatus, item) => (
        <Space size={4} wrap>
          <StatusTag status={value} />
          {item.lockedUntil && dayjs(item.lockedUntil).isAfter(dayjs()) && (
            <Tooltip title={`Khóa tạm đến ${formatDateTime(item.lockedUntil)}`}>
              <Tag color="red">Khóa tạm</Tag>
            </Tooltip>
          )}
          {item.mustChangePassword && <Tag color="orange">Chờ đổi mật khẩu</Tag>}
        </Space>
      ),
    },
    {
      title: 'Đăng nhập gần nhất',
      dataIndex: 'lastLoginAt',
      sorter: true,
      width: 160,
      render: formatDateTime,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, item) => {
        const items = actionsOf(item);
        return (
          <Space>
            {can('user:update') && (
              <Button
                size="small"
                onClick={() => setDialog({ kind: 'edit', user: item })}
                icon={<EditOutlined />}
              >
                Sửa
              </Button>
            )}
            {items && items.length > 0 && (
              <Dropdown menu={{ items, onClick: ({ key }) => onAction(item, key) }}>
                <Button size="small">
                  Khác <DownOutlined />
                </Button>
              </Dropdown>
            )}
          </Space>
        );
      },
    },
  ];

  const target = dialog && dialog.kind !== 'create' ? dialog.user : null;

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Người dùng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo tên đăng nhập, họ tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        createLabel="Thêm người dùng"
        onCreate={can('user:create') ? () => setDialog({ kind: 'create' }) : undefined}
      />
      <Table<UserAccount> {...tableProps} columns={columns} scroll={{ x: 1100 }} />

      <FormModal<CreateUserInput>
        open={dialog?.kind === 'create'}
        title="Thêm người dùng"
        width={680}
        initialValues={{
          companyId: me?.companyId,
          defaultBranchId: me?.defaultBranchId ?? undefined,
          ownDataOnly: false,
          roleIds: [],
          dataScopes: [],
        }}
        fieldNames={[
          'username',
          'fullName',
          'email',
          'phone',
          'companyId',
          'defaultBranchId',
          'initialPassword',
          'roleIds',
          'dataScopes',
        ]}
        onSubmit={async (values, key) => {
          await userApi.create(values, key);
          message.success('Đã tạo người dùng. Người dùng phải đổi mật khẩu ở lần đăng nhập đầu.');
          await refresh();
        }}
        onClose={close}
      >
        <Form.Item
          name="username"
          label="Tên đăng nhập"
          rules={[
            { required: true, message: 'Nhập tên đăng nhập' },
            { pattern: /^[a-z0-9._-]{3,50}$/, message: '3-50 ký tự a-z, 0-9, dấu . _ -' },
          ]}
        >
          <Input maxLength={50} autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="fullName"
          label="Họ tên"
          rules={[{ required: true, whitespace: true, message: 'Nhập họ tên' }]}
        >
          <Input maxLength={150} />
        </Form.Item>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}>
            <Input maxLength={150} style={{ width: 300 }} />
          </Form.Item>
          <Form.Item name="phone" label="Điện thoại">
            <Input maxLength={30} style={{ width: 300 }} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="companyId" label="Công ty" rules={[{ required: true, message: 'Chọn công ty' }]}>
            <Select options={companies.options} style={{ width: 300 }} />
          </Form.Item>
          <Form.Item name="defaultBranchId" label="Chi nhánh mặc định">
            <Select options={branches.options} allowClear style={{ width: 300 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="initialPassword"
          label="Mật khẩu ban đầu"
          extra="Người dùng phải đổi ở lần đăng nhập đầu. Tối thiểu 8 ký tự, có chữ và số."
          rules={PASSWORD_RULES}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="roleIds" label="Vai trò">
          <Select mode="multiple" options={roleOptions} optionFilterProp="label" />
        </Form.Item>
        <Form.Item label="Phạm vi dữ liệu">
          <DataScopeFields />
        </Form.Item>
        <Form.Item name="ownDataOnly" valuePropName="checked">
          <Checkbox>Chỉ xem dữ liệu do mình phụ trách</Checkbox>
        </Form.Item>
      </FormModal>

      <FormModal<UpdateUserInput>
        open={dialog?.kind === 'edit'}
        title={`Sửa người dùng ${target?.username ?? ''}`}
        initialValues={target ? formValues(target) : {}}
        fieldNames={['fullName', 'email', 'phone', 'defaultBranchId']}
        onSubmit={async (values) => {
          if (!target) return;
          await userApi.update(target.id, { ...values, version: target.version });
          message.success('Đã lưu người dùng.');
          await refresh();
        }}
        onClose={close}
      >
        <Form.Item
          name="fullName"
          label="Họ tên"
          rules={[{ required: true, whitespace: true, message: 'Nhập họ tên' }]}
        >
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}>
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="phone" label="Điện thoại">
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="defaultBranchId" label="Chi nhánh mặc định">
          <Select options={branches.options} allowClear />
        </Form.Item>
        <Form.Item name="ownDataOnly" valuePropName="checked">
          <Checkbox>Chỉ xem dữ liệu do mình phụ trách</Checkbox>
        </Form.Item>
      </FormModal>

      <FormModal<{ roleIds: number[] }>
        open={dialog?.kind === 'roles'}
        title={`Vai trò của ${target?.username ?? ''}`}
        initialValues={{ roleIds: target?.roles.map((role) => role.id) ?? [] }}
        fieldNames={['roleIds']}
        onSubmit={async (values) => {
          if (!target) return;
          await userApi.assignRoles(target.id, values.roleIds ?? []);
          message.success('Đã cập nhật vai trò.');
          await refresh();
        }}
        onClose={close}
      >
        <Form.Item
          name="roleIds"
          label="Vai trò"
          extra="Chỉ gán được vai trò có quyền nằm trong quyền của bạn."
        >
          <Select mode="multiple" options={roleOptions} optionFilterProp="label" />
        </Form.Item>
      </FormModal>

      <FormModal<{ dataScopes: DataScope[] }>
        open={dialog?.kind === 'scopes'}
        title={`Phạm vi dữ liệu của ${target?.username ?? ''}`}
        width={620}
        initialValues={{ dataScopes: target?.dataScopes ?? [] }}
        fieldNames={['dataScopes']}
        onSubmit={async (values) => {
          if (!target) return;
          await userApi.assignDataScopes(target.id, values.dataScopes ?? []);
          message.success('Đã cập nhật phạm vi dữ liệu.');
          await refresh();
        }}
        onClose={close}
      >
        <DataScopeFields />
      </FormModal>

      <FormModal<{ newPassword: string }>
        open={dialog?.kind === 'password'}
        title={`Đặt lại mật khẩu cho ${target?.username ?? ''}`}
        okText="Đặt lại"
        fieldNames={['newPassword']}
        onSubmit={async (values, key) => {
          if (!target) return;
          await userApi.resetPassword(target.id, values.newPassword, key);
          message.success('Đã đặt lại mật khẩu. Mọi phiên của người dùng đã bị đăng xuất.');
          await refresh();
        }}
        onClose={close}
      >
        <Form.Item
          name="newPassword"
          label="Mật khẩu mới"
          extra="Người dùng phải đổi mật khẩu này ở lần đăng nhập tiếp theo."
          rules={PASSWORD_RULES}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </FormModal>

      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
