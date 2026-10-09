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
import { formValues } from '../../../shared/forms';
import { branchApi, type BranchInput } from '../api';
import { useCompanies } from '../lookups';
import type { Branch } from '../types';

const FIELDS = ['companyId', 'code', 'name', 'address', 'phone'];

export function BranchesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companies = useCompanies();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [companyId, setCompanyId] = useState<number>();
  const [editing, setEditing] = useState<Branch | 'new' | null>(null);
  const { tableProps } = useServerTable('branches', branchApi.list, { keyword, status, companyId });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['branches'] });
  const statusChange = useStatusChange(
    branchApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );

  const save = async (values: BranchInput, key: string) => {
    if (editing === 'new') {
      await branchApi.create(values, key);
      message.success('Đã tạo chi nhánh. Đăng nhập lại để chọn chi nhánh mới làm việc.');
    } else if (editing) {
      await branchApi.update(editing.id, { ...values, version: editing.version });
      message.success('Đã lưu chi nhánh.');
    }
    await refresh();
  };

  const columns: TableColumnsType<Branch> = [
    { title: 'Mã', dataIndex: 'code', sorter: true, width: 110 },
    { title: 'Tên chi nhánh', dataIndex: 'name', sorter: true },
    { title: 'Công ty', dataIndex: 'companyId', render: (id: number) => companies.nameOf(id) },
    { title: 'Điện thoại', dataIndex: 'phone', width: 140 },
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
        can('branch:update') && (
          <Space>
            <Button size="small" onClick={() => setEditing(item)}>
              Sửa
            </Button>
            <Button size="small" danger={item.status === 'ACTIVE'} onClick={() => statusChange.open(item)}>
              {item.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Chi nhánh
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          companies.items.length > 1 && (
            <Select
              placeholder="Công ty"
              allowClear
              value={companyId}
              onChange={setCompanyId}
              options={companies.options}
              style={{ width: 240 }}
            />
          )
        }
        createLabel="Thêm chi nhánh"
        onCreate={can('branch:create') ? () => setEditing('new') : undefined}
      />
      <Table<Branch> {...tableProps} columns={columns} scroll={{ x: 900 }} />

      <FormModal<BranchInput>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm chi nhánh' : 'Sửa chi nhánh'}
        initialValues={
          editing && editing !== 'new' ? formValues(editing) : { companyId: companies.items[0]?.id }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item name="companyId" label="Công ty" rules={[{ required: true, message: 'Chọn công ty' }]}>
          <Select options={companies.options} disabled={editing !== 'new'} />
        </Form.Item>
        <Form.Item
          name="code"
          label="Mã chi nhánh"
          rules={[
            { required: true, message: 'Nhập mã' },
            { pattern: /^[A-Z0-9_]{1,20}$/, message: 'Mã gồm 1-20 ký tự A-Z, 0-9, _' },
          ]}
        >
          <Input disabled={editing !== 'new'} maxLength={20} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên chi nhánh"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item name="address" label="Địa chỉ">
          <Input maxLength={500} />
        </Form.Item>
        <Form.Item name="phone" label="Điện thoại">
          <Input maxLength={30} />
        </Form.Item>
      </FormModal>

      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
