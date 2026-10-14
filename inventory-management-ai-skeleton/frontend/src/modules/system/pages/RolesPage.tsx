import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { formValues } from '../../../shared/forms';
import { roleApi } from '../api';
import type { Permission, Role } from '../types';

type Dialog = { kind: 'create' } | { kind: 'edit'; role: Role } | { kind: 'permissions'; role: Role };

const MODULE_NAMES: Record<string, string> = { SYSTEM: 'Quản trị hệ thống' };

interface RoleForm {
  code: string;
  name: string;
  description?: string;
}

export function RolesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const { tableProps } = useServerTable('roles', roleApi.list, { keyword, status });
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: roleApi.permissions });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['roles'] });
  const statusChange = useStatusChange(
    roleApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const close = () => setDialog(null);
  const target = dialog && dialog.kind !== 'create' ? dialog.role : null;

  const columns: TableColumnsType<Role> = [
    {
      title: 'Mã',
      dataIndex: 'code',
      sorter: true,
      width: 200,
      render: (code: string, role) => (
        <Space size={4}>
          {code}
          {role.systemRole && <Tag color="blue">Hệ thống</Tag>}
        </Space>
      ),
    },
    { title: 'Tên vai trò', dataIndex: 'name', sorter: true },
    { title: 'Số quyền', dataIndex: 'permissions', width: 100, render: (value: string[]) => value.length },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 260,
      render: (_, role) => (
        <Space>
          {can('role:update') && (
            <Button size="small" onClick={() => setDialog({ kind: 'edit', role })} icon={<EditOutlined />}>
              Sửa
            </Button>
          )}
          {can('role:assign-permissions') && (
            <Button size="small" onClick={() => setDialog({ kind: 'permissions', role })}>
              Phân quyền
            </Button>
          )}
          {can('role:update') && !(role.systemRole && role.status === 'ACTIVE') && (
            <Button
              size="small"
              danger={role.status === 'ACTIVE'}
              onClick={() => statusChange.open(role)}
              icon={role.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {role.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Vai trò và phân quyền
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        createLabel="Thêm vai trò"
        onCreate={can('role:create') ? () => setDialog({ kind: 'create' }) : undefined}
      />
      <Table<Role> {...tableProps} columns={columns} scroll={{ x: 900 }} />

      <FormModal<RoleForm>
        open={dialog?.kind === 'create' || dialog?.kind === 'edit'}
        title={dialog?.kind === 'create' ? 'Thêm vai trò' : 'Sửa vai trò'}
        initialValues={target ? formValues(target) : {}}
        fieldNames={['code', 'name', 'description']}
        onSubmit={async (values, key) => {
          if (dialog?.kind === 'create') {
            await roleApi.create(values, key);
            message.success('Đã tạo vai trò. Bấm "Phân quyền" để gán quyền.');
          } else if (target) {
            await roleApi.update(target.id, {
              name: values.name,
              description: values.description,
              version: target.version,
            });
            message.success('Đã lưu vai trò.');
          }
          await refresh();
        }}
        onClose={close}
      >
        <Form.Item
          name="code"
          label="Mã vai trò"
          rules={[
            { required: true, message: 'Nhập mã' },
            { pattern: /^[A-Z0-9_]{2,50}$/, message: '2-50 ký tự A-Z, 0-9, _' },
          ]}
        >
          <Input disabled={dialog?.kind !== 'create'} maxLength={50} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên vai trò"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="description" label="Mô tả">
          <Input.TextArea rows={2} maxLength={500} />
        </Form.Item>
      </FormModal>

      <FormModal<{ permissionCodes: string[] }>
        open={dialog?.kind === 'permissions'}
        title={`Phân quyền: ${target?.name ?? ''}`}
        width={720}
        initialValues={{ permissionCodes: target?.permissions ?? [] }}
        fieldNames={['permissionCodes']}
        onSubmit={async (values) => {
          if (!target) return;
          await roleApi.assignPermissions(target.id, values.permissionCodes ?? []);
          message.success(
            'Đã cập nhật quyền. Người dùng có vai trò này nhận quyền mới ngay ở thao tác tiếp theo.',
          );
          await refresh();
        }}
        onClose={close}
      >
        <Typography.Paragraph type="secondary">
          Chỉ gán được quyền mà bạn đang có; quyền khác sẽ bị máy chủ từ chối.
        </Typography.Paragraph>
        <Form.Item name="permissionCodes">
          <Checkbox.Group style={{ width: '100%' }}>
            {groupByModule(permissions.data ?? []).map(([module, items]) => (
              <div key={module} style={{ marginBottom: 12 }}>
                <Typography.Text strong>{MODULE_NAMES[module] ?? module}</Typography.Text>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
                    marginTop: 4,
                  }}
                >
                  {items.map((permission) => (
                    <Checkbox key={permission.code} value={permission.code} disabled={!can(permission.code)}>
                      {permission.name}
                    </Checkbox>
                  ))}
                </div>
              </div>
            ))}
          </Checkbox.Group>
        </Form.Item>
      </FormModal>

      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}

function groupByModule(permissions: Permission[]): [string, Permission[]][] {
  const groups = new Map<string, Permission[]>();
  permissions.forEach((permission) => {
    groups.set(permission.module, [...(groups.get(permission.module) ?? []), permission]);
  });
  return [...groups.entries()];
}
