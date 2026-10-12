import { UploadOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Checkbox,
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
import { useMemo, useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { ImportModal } from '../../masterdata/components/ImportModal';
import { useCompanyId } from '../../masterdata/lookups';
import { accountApi } from '../api';
import { BALANCE_SIDES } from '../labels';
import type { Account, BalanceSide } from '../types';

interface TreeRow extends Account {
  children?: TreeRow[];
}

interface AccountForm {
  code?: string;
  name: string;
  balanceSide: BalanceSide;
  partnerRequired: boolean;
}

type Editing = { mode: 'new'; parent: Account | null } | { mode: 'edit'; account: Account } | null;

const FIELDS = ['code', 'name', 'parentId', 'balanceSide', 'partnerRequired'];

function buildTree(items: Account[]): TreeRow[] {
  const byId = new Map<number, TreeRow>(items.map((item) => [item.id, { ...item }]));
  const roots: TreeRow[] = [];
  items.forEach((item) => {
    const row = byId.get(item.id) as TreeRow;
    const parent = item.parentId === null ? undefined : byId.get(item.parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), row];
    } else {
      roots.push(row);
    }
  });
  return roots;
}

/**
 * Hệ thống tài khoản dạng cây (BR-ACC-01). Tài khoản có tài khoản con là tài khoản tổng hợp, không hạch toán trực
 * tiếp. Production nạp bộ tài khoản theo chế độ kế toán bằng nhập Excel sau khi kế toán trưởng xác nhận.
 */
export function AccountsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const [editing, setEditing] = useState<Editing>(null);
  const [importing, setImporting] = useState(false);
  const query = useQuery({
    queryKey: ['accounts', 'tree', companyId],
    queryFn: () => accountApi.tree(companyId),
    enabled: companyId > 0,
  });
  const tree = useMemo(() => buildTree(query.data ?? []), [query.data]);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['accounts'] });
  const statusChange = useStatusChange(
    accountApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('account:manage');

  const save = async (values: AccountForm, key: string) => {
    if (editing?.mode === 'new') {
      await accountApi.create(
        {
          companyId,
          code: values.code,
          name: values.name,
          parentId: editing.parent?.id ?? null,
          balanceSide: values.balanceSide,
          partnerRequired: values.partnerRequired ?? false,
        },
        key,
      );
      message.success('Đã thêm tài khoản.');
    } else if (editing?.mode === 'edit') {
      await accountApi.update(editing.account.id, {
        name: values.name,
        balanceSide: values.balanceSide,
        partnerRequired: values.partnerRequired ?? false,
        version: editing.account.version,
      });
      message.success('Đã lưu tài khoản.');
    }
    await refresh();
  };

  const columns: TableColumnsType<TreeRow> = [
    { title: 'Số tài khoản', dataIndex: 'code', width: 220 },
    { title: 'Tên tài khoản', dataIndex: 'name' },
    {
      title: 'Tính chất',
      dataIndex: 'balanceSide',
      width: 120,
      render: (value: BalanceSide) => BALANCE_SIDES[value],
    },
    {
      title: 'Loại',
      dataIndex: 'postable',
      width: 120,
      render: (postable: boolean) => (postable ? <Tag color="blue">Chi tiết</Tag> : <Tag>Tổng hợp</Tag>),
    },
    {
      title: 'Theo dõi đối tượng',
      dataIndex: 'partnerRequired',
      width: 150,
      render: (value: boolean) => (value ? 'Có' : ''),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 260,
      render: (_, row) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing({ mode: 'edit', account: row })}>
              Sửa
            </Button>
            {row.status === 'ACTIVE' && (
              <Button size="small" onClick={() => setEditing({ mode: 'new', parent: row })}>
                Thêm TK con
              </Button>
            )}
            <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => statusChange.open(row)}>
              {row.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  const parent = editing?.mode === 'new' ? editing.parent : null;
  const initialValues: Partial<AccountForm> =
    editing?.mode === 'edit'
      ? {
          code: editing.account.code,
          name: editing.account.name,
          balanceSide: editing.account.balanceSide,
          partnerRequired: editing.account.partnerRequired,
        }
      : { code: parent?.code, balanceSide: parent?.balanceSide ?? 'DEBIT', partnerRequired: false };

  return (
    <Card>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Hệ thống tài khoản
        </Typography.Title>
        {canManage && (
          <Space>
            <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>
              Nhập từ Excel
            </Button>
            <Button type="primary" onClick={() => setEditing({ mode: 'new', parent: null })}>
              Thêm tài khoản cấp 1
            </Button>
          </Space>
        )}
      </Space>
      <Table<TreeRow>
        rowKey="id"
        size="small"
        loading={query.isFetching}
        dataSource={tree}
        columns={columns}
        pagination={false}
        scroll={{ x: 1000 }}
        expandable={{ defaultExpandAllRows: true }}
      />
      <FormModal<AccountForm>
        open={editing !== null}
        title={
          editing?.mode === 'edit'
            ? 'Sửa tài khoản'
            : parent
              ? `Thêm tài khoản con của ${parent.code}`
              : 'Thêm tài khoản cấp 1'
        }
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item
          name="code"
          label="Số tài khoản"
          extra={parent ? `Bắt đầu bằng ${parent.code}` : undefined}
          rules={
            editing?.mode === 'new'
              ? [
                  { required: true, message: 'Nhập số tài khoản' },
                  { pattern: /^[0-9A-Z_.-]{1,20}$/, message: 'Gồm 1-20 ký tự 0-9, A-Z, _ . -' },
                ]
              : []
          }
        >
          <Input disabled={editing?.mode === 'edit'} maxLength={20} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên tài khoản"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item
          name="balanceSide"
          label="Tính chất"
          rules={[{ required: true, message: 'Chọn tính chất' }]}
        >
          <Select options={Object.entries(BALANCE_SIDES).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item name="partnerRequired" valuePropName="checked">
          <Checkbox>Bắt buộc chi tiết theo đối tượng (khách hàng, nhà cung cấp, nhân viên)</Checkbox>
        </Form.Item>
      </FormModal>
      <ImportModal
        open={importing}
        title="Nhập hệ thống tài khoản từ Excel"
        onDownloadTemplate={accountApi.downloadTemplate}
        onImport={(file, key) => accountApi.importFile(file, companyId, key)}
        onDone={() => void refresh()}
        onClose={() => setImporting(false)}
      />
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
