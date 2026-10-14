import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Space, Table, Typography, type TableColumnsType } from 'antd';
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
import { companyApi, type CompanyInput } from '../api';
import type { Company } from '../types';

const FIELDS = ['code', 'name', 'taxCode', 'address', 'phone', 'email'];

export function CompaniesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [editing, setEditing] = useState<Company | 'new' | null>(null);
  const { tableProps } = useServerTable('companies', companyApi.list, { keyword, status });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['companies'] });
  const statusChange = useStatusChange(
    companyApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );

  const save = async (values: CompanyInput, key: string) => {
    if (editing === 'new') {
      await companyApi.create(values, key);
      message.success('Đã tạo công ty.');
    } else if (editing) {
      await companyApi.update(editing.id, { ...values, version: editing.version });
      message.success('Đã lưu công ty.');
    }
    await refresh();
  };

  const columns: TableColumnsType<Company> = [
    { title: 'Mã', dataIndex: 'code', sorter: true, width: 120 },
    { title: 'Tên công ty', dataIndex: 'name', sorter: true },
    { title: 'Mã số thuế', dataIndex: 'taxCode', width: 140 },
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
        can('company:update') && (
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
        Công ty
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        createLabel="Thêm công ty"
        onCreate={can('company:create') ? () => setEditing('new') : undefined}
      />
      <Table<Company> {...tableProps} columns={columns} scroll={{ x: 900 }} />

      <FormModal<CompanyInput>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm công ty' : 'Sửa công ty'}
        initialValues={editing && editing !== 'new' ? formValues(editing) : {}}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item
          name="code"
          label="Mã công ty"
          rules={[
            { required: true, message: 'Nhập mã' },
            { pattern: /^[A-Z0-9_]{1,20}$/, message: 'Mã gồm 1-20 ký tự A-Z, 0-9, _' },
          ]}
        >
          <Input disabled={editing !== 'new'} maxLength={20} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên công ty"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item name="taxCode" label="Mã số thuế">
          <Input maxLength={20} />
        </Form.Item>
        <Form.Item name="address" label="Địa chỉ">
          <Input maxLength={500} />
        </Form.Item>
        <Form.Item name="phone" label="Điện thoại">
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}>
          <Input maxLength={150} />
        </Form.Item>
      </FormModal>

      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
