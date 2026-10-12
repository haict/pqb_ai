import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { formatDate } from '../../inventory/labels';
import { PARTNER_ROLES, PRODUCT_TYPES } from '../../masterdata/labels';
import { useCompanyId } from '../../masterdata/lookups';
import type { PartnerRole, ProductType } from '../../masterdata/types';
import { useDocumentTypes } from '../../system/lookups';
import { postingRuleApi } from '../api';
import { usePostableAccounts } from '../lookups';
import type { PostingRule } from '../types';

interface RuleForm {
  documentType: string;
  transactionCode: string;
  productType?: ProductType;
  partnerRole?: PartnerRole;
  debitAccountId?: number;
  creditAccountId?: number;
  validFrom: Dayjs;
  validTo?: Dayjs;
  description?: string;
}

const FIELDS = [
  'documentType',
  'transactionCode',
  'productType',
  'partnerRole',
  'debitAccountId',
  'creditAccountId',
  'validFrom',
  'validTo',
  'description',
];

const BY_DOCUMENT = 'Theo chứng từ';

/**
 * Quy tắc hạch toán (BR-ACC-02, ADR-11): loại chứng từ + mã nghiệp vụ (+ loại hàng, vai trò đối tượng) ra tài khoản
 * Nợ / Có. Vế để trống là tài khoản do chứng từ quyết định (ví dụ tài khoản tiền của quỹ). Điều kiện và ngày bắt đầu
 * không sửa được; đổi quy tắc bằng cách đặt ngày hết hiệu lực rồi thêm quy tắc mới.
 */
export function PostingRulesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const documentTypes = useDocumentTypes();
  const accounts = usePostableAccounts();
  const [transactionCode, setTransactionCode] = useState('');
  const [documentType, setDocumentType] = useState<string>();
  const [status, setStatus] = useState<RecordStatus>();
  const [editing, setEditing] = useState<PostingRule | 'new' | null>(null);
  const { tableProps } = useServerTable('posting-rules', postingRuleApi.list, {
    companyId,
    documentType,
    transactionCode,
    status,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['posting-rules'] });
  const statusChange = useStatusChange(
    postingRuleApi.changeStatus,
    (item) => `${item.documentType} / ${item.transactionCode}`,
    refresh,
  );
  const canManage = can('posting-rule:manage');

  const save = async (values: RuleForm, key: string) => {
    const common = {
      debitAccountId: values.debitAccountId ?? null,
      creditAccountId: values.creditAccountId ?? null,
      validTo: values.validTo ? values.validTo.format('YYYY-MM-DD') : null,
      description: values.description,
    };
    if (editing === 'new') {
      await postingRuleApi.create(
        {
          ...common,
          companyId,
          documentType: values.documentType,
          transactionCode: values.transactionCode,
          productType: values.productType ?? null,
          partnerRole: values.partnerRole ?? null,
          validFrom: values.validFrom.format('YYYY-MM-DD'),
        },
        key,
      );
      message.success('Đã thêm quy tắc.');
    } else if (editing) {
      await postingRuleApi.update(editing.id, { ...common, version: editing.version });
      message.success('Đã lưu quy tắc.');
    }
    await refresh();
  };

  const columns: TableColumnsType<PostingRule> = [
    {
      title: 'Loại chứng từ',
      dataIndex: 'documentType',
      width: 200,
      sorter: true,
      render: (value: string) => `${value} - ${documentTypes.nameOf(value)}`,
    },
    { title: 'Mã nghiệp vụ', dataIndex: 'transactionCode', width: 200, sorter: true },
    {
      title: 'Điều kiện',
      width: 200,
      render: (_, rule) =>
        [
          rule.productType && PRODUCT_TYPES[rule.productType as ProductType],
          rule.partnerRole && PARTNER_ROLES[rule.partnerRole as PartnerRole],
        ]
          .filter(Boolean)
          .join(', ') || 'Mọi trường hợp',
    },
    { title: 'TK Nợ', dataIndex: 'debitAccountCode', width: 110, render: (value) => value ?? BY_DOCUMENT },
    { title: 'TK Có', dataIndex: 'creditAccountCode', width: 110, render: (value) => value ?? BY_DOCUMENT },
    {
      title: 'Hiệu lực',
      dataIndex: 'validFrom',
      width: 200,
      sorter: true,
      render: (_, rule) =>
        `${formatDate(rule.validFrom)} - ${rule.validTo ? formatDate(rule.validTo) : '...'}`,
    },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, rule) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing(rule)}>
              Sửa
            </Button>
            <Button size="small" danger={rule.status === 'ACTIVE'} onClick={() => statusChange.open(rule)}>
              {rule.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  const isNew = editing === 'new';
  const initialValues: Partial<RuleForm> =
    editing && editing !== 'new'
      ? {
          documentType: editing.documentType,
          transactionCode: editing.transactionCode,
          productType: (editing.productType as ProductType | null) ?? undefined,
          partnerRole: (editing.partnerRole as PartnerRole | null) ?? undefined,
          debitAccountId: editing.debitAccountId ?? undefined,
          creditAccountId: editing.creditAccountId ?? undefined,
          validFrom: dayjs(editing.validFrom),
          validTo: editing.validTo ? dayjs(editing.validTo) : undefined,
          description: editing.description ?? undefined,
        }
      : { validFrom: dayjs() };

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Quy tắc hạch toán
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm mã nghiệp vụ"
        onSearch={setTransactionCode}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Loại chứng từ"
            style={{ width: 260 }}
            value={documentType}
            onChange={setDocumentType}
            options={documentTypes.options}
          />
        }
        createLabel="Thêm quy tắc"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<PostingRule> {...tableProps} size="small" columns={columns} scroll={{ x: 1400 }} />
      <FormModal<RuleForm>
        open={editing !== null}
        title={isNew ? 'Thêm quy tắc hạch toán' : 'Sửa quy tắc hạch toán'}
        width={680}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item
          name="documentType"
          label="Loại chứng từ"
          rules={[{ required: true, message: 'Chọn loại' }]}
        >
          <Select disabled={!isNew} showSearch optionFilterProp="label" options={documentTypes.options} />
        </Form.Item>
        <Form.Item
          name="transactionCode"
          label="Mã nghiệp vụ"
          rules={[
            { required: true, message: 'Nhập mã nghiệp vụ' },
            { pattern: /^[A-Z0-9_]{1,50}$/, message: 'Gồm 1-50 ký tự A-Z, 0-9, _' },
          ]}
        >
          <Input disabled={!isNew} maxLength={50} placeholder="Ví dụ RECEIPT_CUSTOMER" />
        </Form.Item>
        <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
          <Form.Item name="productType" label="Loại hàng (nếu cần)">
            <Select
              allowClear
              disabled={!isNew}
              options={Object.entries(PRODUCT_TYPES).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="partnerRole" label="Vai trò đối tượng (nếu cần)">
            <Select
              allowClear
              disabled={!isNew}
              options={Object.entries(PARTNER_ROLES).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Space>
        <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
          <Form.Item name="debitAccountId" label="TK Nợ" extra="Để trống: tài khoản do chứng từ quyết định">
            <Select allowClear showSearch optionFilterProp="label" options={accounts.options} />
          </Form.Item>
          <Form.Item name="creditAccountId" label="TK Có" extra="Để trống: tài khoản do chứng từ quyết định">
            <Select allowClear showSearch optionFilterProp="label" options={accounts.options} />
          </Form.Item>
        </Space>
        <Space style={{ width: '100%' }} styles={{ item: { flex: 1 } }}>
          <Form.Item name="validFrom" label="Hiệu lực từ" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <DatePicker disabled={!isNew} format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="validTo" label="Đến ngày">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="description" label="Diễn giải">
          <Input.TextArea rows={2} maxLength={500} />
        </Form.Item>
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
