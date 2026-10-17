import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  Row,
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
import { formatMoney } from '../../accounting/labels';
import { usePostableAccounts } from '../../accounting/lookups';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { cashAccountApi } from '../api';
import { CASH_ACCOUNT_TYPES } from '../labels';
import type { CashAccount, CashAccountType } from '../types';
import { whenBranchVisible } from '../../../shared/branch';
import { BranchCol } from '../../../shared/components/BranchCol';

interface AccountForm {
  branchId: number;
  code?: string;
  name: string;
  accountType: CashAccountType;
  glAccountId: number;
  bankName?: string;
  bankAccountNo?: string;
}

const FIELDS = ['branchId', 'code', 'name', 'accountType', 'glAccountId', 'bankName', 'bankAccountNo'];

/**
 * Quỹ tiền mặt, tài khoản ngân hàng (BR-CSH-01, BR-MD-46). Số dư chỉ thay đổi khi ghi sổ chứng từ thu chi; quỹ còn số
 * dư không ngừng được.
 */
export function CashAccountsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const accounts = usePostableAccounts();
  const [keyword, setKeyword] = useState('');
  const [accountType, setAccountType] = useState<CashAccountType>();
  const [status, setStatus] = useState<RecordStatus>();
  const [editing, setEditing] = useState<CashAccount | 'new' | null>(null);
  const { tableProps } = useServerTable('cash-accounts', cashAccountApi.list, {
    companyId,
    keyword,
    accountType,
    status,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['cash-accounts'] });
  const statusChange = useStatusChange(cashAccountApi.changeStatus, (item) => item.code, refresh);
  const canManage = can('cash-account:manage');

  const save = async (values: AccountForm, key: string) => {
    const common = {
      name: values.name,
      glAccountId: values.glAccountId,
      bankName: values.bankName,
      bankAccountNo: values.bankAccountNo,
    };
    if (editing === 'new') {
      await cashAccountApi.create(
        { ...common, branchId: values.branchId, code: values.code, accountType: values.accountType },
        key,
      );
      message.success('Đã thêm quỹ.');
    } else if (editing) {
      await cashAccountApi.update(editing.id, { ...common, version: editing.version });
      message.success('Đã lưu quỹ.');
    }
    await refresh();
  };

  const columns: TableColumnsType<CashAccount> = [
    { title: 'Mã', dataIndex: 'code', width: 140, sorter: true },
    { title: 'Tên', dataIndex: 'name', sorter: true },
    {
      title: 'Loại',
      dataIndex: 'accountType',
      width: 110,
      render: (value: CashAccountType) => CASH_ACCOUNT_TYPES[value],
    },
    ...whenBranchVisible({
      title: 'Chi nhánh',
      dataIndex: 'branchId',
      width: 160,
      render: (value: number) => branches.nameOf(value),
    }),
    { title: 'TK kế toán', dataIndex: 'glAccountCode', width: 110 },
    {
      title: 'Ngân hàng',
      width: 240,
      render: (_, row) => (row.bankAccountNo ? `${row.bankAccountNo} - ${row.bankName ?? ''}` : ''),
    },
    { title: 'Số dư', dataIndex: 'balance', width: 160, align: 'right', sorter: true, render: formatMoney },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
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

  const isNew = editing === 'new';
  const initialValues: Partial<AccountForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          code: editing.code,
          name: editing.name,
          accountType: editing.accountType,
          glAccountId: editing.glAccountId,
          bankName: editing.bankName ?? undefined,
          bankAccountNo: editing.bankAccountNo ?? undefined,
        }
      : { branchId: branches.defaultId, accountType: 'CASH' };

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Quỹ, tài khoản ngân hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Mã, tên, số tài khoản"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <Select
            allowClear
            placeholder="Loại"
            style={{ width: 140 }}
            value={accountType}
            onChange={setAccountType}
            options={Object.entries(CASH_ACCOUNT_TYPES).map(([value, label]) => ({ value, label }))}
          />
        }
        createLabel="Thêm quỹ"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<CashAccount> {...tableProps} size="small" columns={columns} scroll={{ x: 1300 }} />
      <FormModal<AccountForm>
        open={editing !== null}
        title={isNew ? 'Thêm quỹ / tài khoản ngân hàng' : 'Sửa quỹ / tài khoản ngân hàng'}
        width={640}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <AccountFields
            form={form}
            isNew={isNew}
            branchOptions={branches.options}
            glOptions={accounts.options}
          />
        )}
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}

function AccountFields({
  form,
  isNew,
  branchOptions,
  glOptions,
}: {
  form: ReturnType<typeof Form.useForm<AccountForm>>[0];
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
  glOptions: { value: number; label: string }[];
}) {
  const accountType = Form.useWatch('accountType', form) as CashAccountType | undefined;
  return (
    <>
      <Row gutter={8}>
        <BranchCol flex={1}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </BranchCol>
        <Col flex={1}>
          <Form.Item name="accountType" label="Loại" rules={[{ required: true, message: 'Chọn loại' }]}>
            <Select
              disabled={!isNew}
              options={Object.entries(CASH_ACCOUNT_TYPES).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item
        name="code"
        label="Mã"
        rules={
          isNew
            ? [
                { required: true, message: 'Nhập mã' },
                { pattern: /^[A-Z0-9_.-]{1,30}$/, message: 'Gồm 1-30 ký tự A-Z, 0-9, _ . -' },
              ]
            : []
        }
      >
        <Input disabled={!isNew} maxLength={30} />
      </Form.Item>
      <Form.Item name="name" label="Tên" rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}>
        <Input maxLength={200} />
      </Form.Item>
      <Form.Item
        name="glAccountId"
        label="Tài khoản kế toán"
        extra="Tài khoản tiền chi tiết, ví dụ 1111 (tiền mặt), 1121 (tiền gửi)"
        rules={[{ required: true, message: 'Chọn tài khoản' }]}
      >
        <Select showSearch optionFilterProp="label" options={glOptions} />
      </Form.Item>
      {accountType === 'BANK' && (
        <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
          <Form.Item
            name="bankAccountNo"
            label="Số tài khoản"
            rules={[{ required: true, whitespace: true, message: 'Nhập số tài khoản' }]}
          >
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="bankName" label="Ngân hàng">
            <Input maxLength={200} />
          </Form.Item>
        </Space>
      )}
    </>
  );
}
