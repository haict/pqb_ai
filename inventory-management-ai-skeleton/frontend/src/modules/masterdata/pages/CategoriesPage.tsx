import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Space,
  Table,
  Typography,
  TreeSelect,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { categoryApi } from '../api';
import { useCompanyId } from '../lookups';
import type { ProductCategory } from '../types';

interface TreeRow extends ProductCategory {
  children?: TreeRow[];
}

const FIELDS = ['code', 'name', 'parentId'];

function buildTree(items: ProductCategory[]): TreeRow[] {
  const byId = new Map<number, TreeRow>(items.map((item) => [item.id, { ...item }]));
  const roots: TreeRow[] = [];
  byId.forEach((row) => {
    const parent = row.parentId === null ? undefined : byId.get(row.parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), row];
    } else {
      roots.push(row);
    }
  });
  return roots;
}

interface TreeOption {
  value: number;
  title: string;
  children?: TreeOption[];
}

function toTreeOptions(rows: TreeRow[], excludeId?: number): TreeOption[] {
  return rows
    .filter((row) => row.id !== excludeId)
    .map((row) => ({
      value: row.id,
      title: `${row.code} - ${row.name}`,
      children: row.children ? toTreeOptions(row.children, excludeId) : undefined,
    }));
}

/** Nhóm hàng dạng cây (BR-MD-03). Danh mục nhỏ: tải tối đa 200 nhóm, dựng cây ở giao diện. */
export function CategoriesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const [editing, setEditing] = useState<ProductCategory | 'new' | null>(null);
  const query = useQuery({
    queryKey: ['product-categories', 'tree', companyId],
    queryFn: () => categoryApi.list({ page: 0, size: 200, companyId }),
    enabled: companyId > 0,
  });
  const tree = useMemo(() => buildTree(query.data?.items ?? []), [query.data]);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['product-categories'] });
  const statusChange = useStatusChange(
    categoryApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('catalog:manage');

  const save = async (values: { code?: string; name: string; parentId?: number }, key: string) => {
    if (editing === 'new') {
      await categoryApi.create({ ...values, companyId }, key);
      message.success('Đã thêm nhóm hàng.');
    } else if (editing) {
      await categoryApi.update(editing.id, {
        name: values.name,
        parentId: values.parentId ?? null,
        version: editing.version,
      });
      message.success('Đã lưu nhóm hàng.');
    }
    await refresh();
  };

  const columns: TableColumnsType<TreeRow> = [
    { title: 'Mã', dataIndex: 'code', width: 220 },
    { title: 'Tên nhóm hàng', dataIndex: 'name' },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, row) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing(row)} icon={<EditOutlined />}>
              Sửa
            </Button>
            <Button
              size="small"
              danger={row.status === 'ACTIVE'}
              onClick={() => statusChange.open(row)}
              icon={row.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {row.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <Card>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Nhóm hàng
        </Typography.Title>
        {canManage && (
          <Button type="primary" onClick={() => setEditing('new')}>
            Thêm nhóm hàng
          </Button>
        )}
      </Space>
      <Table<TreeRow>
        rowKey="id"
        loading={query.isFetching}
        dataSource={tree}
        columns={columns}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
      />
      <FormModal<{ code?: string; name: string; parentId?: number }>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm nhóm hàng' : 'Sửa nhóm hàng'}
        initialValues={
          editing && editing !== 'new'
            ? { code: editing.code, name: editing.name, parentId: editing.parentId ?? undefined }
            : {}
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item
          name="code"
          label="Mã nhóm"
          rules={
            editing === 'new'
              ? [
                  { required: true, message: 'Nhập mã' },
                  { pattern: /^[A-Z0-9_.-]{1,30}$/, message: 'Mã gồm 1-30 ký tự A-Z, 0-9, _ . -' },
                ]
              : []
          }
        >
          <Input disabled={editing !== 'new'} maxLength={30} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên nhóm"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="parentId" label="Nhóm cha">
          <TreeSelect
            allowClear
            treeDefaultExpandAll
            treeData={toTreeOptions(tree, editing && editing !== 'new' ? editing.id : undefined)}
          />
        </Form.Item>
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
