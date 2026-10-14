import { EditOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { parameterApi } from '../api';
import { useCompanies } from '../lookups';
import type { SystemParameter } from '../types';

const FIELDS = ['value'];

function constraintText(parameter: SystemParameter): string {
  if (parameter.allowedValues.length > 0) {
    return parameter.allowedValues.join(', ');
  }
  if (parameter.minValue !== null || parameter.maxValue !== null) {
    return `${parameter.minValue ?? '…'} - ${parameter.maxValue ?? '…'}`;
  }
  return '';
}

function ValueInput({ parameter }: { parameter: SystemParameter }) {
  if (parameter.valueType === 'BOOLEAN') {
    return (
      <Select
        options={[
          { value: 'true', label: 'Có' },
          { value: 'false', label: 'Không' },
        ]}
      />
    );
  }
  if (parameter.valueType === 'ENUM') {
    return <Select options={parameter.allowedValues.map((value) => ({ value, label: value }))} />;
  }
  return <Input maxLength={500} />;
}

/** Tham số hệ thống theo công ty (BR-SYS-13). */
export function ParametersPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companies = useCompanies();
  const [selectedCompanyId, setCompanyId] = useState<number>();
  const [editing, setEditing] = useState<SystemParameter | null>(null);
  const companyId = selectedCompanyId ?? companies.items[0]?.id;

  const query = useQuery({
    queryKey: ['parameters', companyId],
    queryFn: () => parameterApi.list(companyId!),
    enabled: companyId !== undefined,
  });

  const save = async (values: { value: string }) => {
    if (!editing || companyId === undefined) {
      return;
    }
    await parameterApi.update(editing.key, { companyId, value: values.value, version: editing.version });
    message.success('Đã lưu tham số.');
    await queryClient.invalidateQueries({ queryKey: ['parameters'] });
  };

  const columns: TableColumnsType<SystemParameter> = [
    {
      title: 'Tham số',
      render: (_, parameter) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{parameter.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {parameter.key}
          </Typography.Text>
        </Space>
      ),
    },
    { title: 'Giá trị', dataIndex: 'value', width: 120 },
    { title: 'Mặc định', dataIndex: 'defaultValue', width: 110 },
    { title: 'Giới hạn', width: 130, render: (_, parameter) => constraintText(parameter) },
    { title: 'Ghi chú', dataIndex: 'description' },
    {
      title: 'Trạng thái',
      width: 170,
      render: (_, parameter) => (
        <Space size={4} wrap>
          {parameter.customized ? <Tag color="blue">Giá trị riêng</Tag> : <Tag>Mặc định</Tag>}
          {parameter.locked && <Tag color="red">Đã khóa</Tag>}
        </Space>
      ),
    },
    {
      title: 'Thao tác',
      width: 100,
      render: (_, parameter) =>
        can('parameter:update') &&
        !parameter.locked && (
          <Button size="small" onClick={() => setEditing(parameter)} icon={<EditOutlined />}>
            Sửa
          </Button>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Tham số hệ thống
      </Typography.Title>
      {companies.items.length > 1 && (
        <Select
          placeholder="Công ty"
          value={companyId}
          onChange={setCompanyId}
          options={companies.options}
          style={{ width: 240, marginBottom: 16 }}
        />
      )}
      <Table<SystemParameter>
        rowKey="key"
        loading={query.isFetching}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        scroll={{ x: 900 }}
      />

      <FormModal<{ value: string }>
        open={editing !== null}
        title={editing ? `Sửa tham số: ${editing.name}` : ''}
        initialValues={editing ? { value: editing.value } : undefined}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {editing && (
          <>
            <Form.Item
              name="value"
              label="Giá trị"
              rules={[{ required: true, whitespace: true, message: 'Nhập giá trị' }]}
              extra={constraintText(editing) && `Giới hạn: ${constraintText(editing)}`}
            >
              <ValueInput parameter={editing} />
            </Form.Item>
            {editing.lockAfterPosting && (
              <Typography.Paragraph type="warning">
                Tham số này sẽ bị khóa khi công ty phát sinh số liệu ghi sổ.
              </Typography.Paragraph>
            )}
          </>
        )}
      </FormModal>
    </Card>
  );
}
