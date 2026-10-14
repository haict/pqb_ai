import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { departmentApi, type DepartmentInput } from '../api';
import { useMyBranches } from '../lookups';
import type { Department } from '../types';

const FIELDS = ['branchId', 'code', 'name'];

export function DepartmentsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [branchId, setBranchId] = useState<number>();
  const [editing, setEditing] = useState<Department | 'new' | null>(null);
  const { tableProps } = useServerTable('departments', departmentApi.list, { keyword, status, branchId });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['departments'] });
  const statusChange = useStatusChange(
    departmentApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );

  const save = async (values: DepartmentInput, key: string) => {
    if (editing === 'new') {
      await departmentApi.create(values, key);
      message.success('Đã tạo phòng ban.');
    } else if (editing) {
      await departmentApi.update(editing.id, { name: values.name, version: editing.version });
      message.success('Đã lưu phòng ban.');
    }
    await refresh();
  };

  const columns: TableColumnsType<Department> = [
    { title: 'Mã', dataIndex: 'code', sorter: true, width: 110 },
    { title: 'Tên phòng ban', dataIndex: 'name', sorter: true },
    { title: 'Chi nhánh', dataIndex: 'branchId', render: (id: number) => branches.nameOf(id) },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 180,
      render: (_, item) =>
        can('department:update') && (
          <Space>
            <Button size="small" onClick={() => setEditing(item)} icon={<EditOutlined />}>
              Sửa
            </Button>
            <Button
              size="small"
              danger={item.status === 'ACTIVE'}
              onClick={() => statusChange.open(item)}
              icon={item.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {item.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Phòng ban
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <Select
            placeholder="Chi nhánh"
            allowClear
            value={branchId}
            onChange={setBranchId}
            options={branches.options}
            style={{ width: 240 }}
          />
        }
        createLabel="Thêm phòng ban"
        onCreate={can('department:create') ? () => setEditing('new') : undefined}
      />
      <Table<Department> {...tableProps} columns={columns} scroll={{ x: 800 }} />

      <FormModal<DepartmentInput>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm phòng ban' : 'Sửa phòng ban'}
        initialValues={editing && editing !== 'new' ? editing : { branchId }}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true, message: 'Chọn chi nhánh' }]}>
          <Select options={branches.options} disabled={editing !== 'new'} />
        </Form.Item>
        <Form.Item
          name="code"
          label="Mã phòng ban"
          rules={[
            { required: true, message: 'Nhập mã' },
            { pattern: /^[A-Z0-9_]{1,20}$/, message: 'Mã gồm 1-20 ký tự A-Z, 0-9, _' },
          ]}
        >
          <Input disabled={editing !== 'new'} maxLength={20} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên phòng ban"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={255} />
        </Form.Item>
      </FormModal>

      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
