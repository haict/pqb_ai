import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { policyApi } from '../api';
import { DocumentActions } from '../components/DocumentActions';
import { formatDate, formatNumber, statusLabel } from '../labels';
import { DocumentStatusTag } from '../components/StatusTags';
import type { PolicyStatus, StockIssuePolicy } from '../types';
import { DecimalInput } from '../../../shared/components/DecimalInput';

const STATUSES: PolicyStatus[] = ['DRAFT', 'APPROVED', 'CANCELLED'];

/** APPROVED của chính sách nghĩa là có hiệu lực (D-61: kích hoạt thay bước duyệt). */
function policyStatusLabel(status: PolicyStatus): string {
  return status === 'APPROVED' ? 'Có hiệu lực' : statusLabel(status);
}

function PolicyStatusTag({ status }: { status: PolicyStatus }) {
  return status === 'APPROVED' ? <Tag color="blue">Có hiệu lực</Tag> : <DocumentStatusTag status={status} />;
}

interface PolicyForm {
  warehouseId: number;
  productId?: number;
  maxQuantity: number;
  validRange: [Dayjs, Dayjs];
  reason: string;
}

/**
 * Không qua duyệt (D-61): người có quyền stock-policy:activate (mặc định Giám đốc) kích hoạt từ nháp; trạng thái
 * APPROVED nghĩa là có hiệu lực. Chính sách chờ duyệt / bị từ chối cũ chỉ còn hủy.
 */
function policyActions(status: PolicyStatus, can: (permission: string) => boolean): string[] {
  const cancel = can('stock-policy:create') ? ['cancel'] : [];
  const actions: Record<PolicyStatus, string[]> = {
    DRAFT: [...(can('stock-policy:activate') ? ['activate'] : []), ...cancel],
    REJECTED: cancel,
    SUBMITTED: cancel,
    APPROVED: cancel,
    CANCELLED: [],
  };
  return actions[status];
}

function PolicyDrawer({ id, onClose }: { id: number; onClose(): void }) {
  const can = useCan();
  const queryClient = useQueryClient();
  const warehouses = useWarehouses();
  const query = useQuery({ queryKey: ['stock-issue-policy', id], queryFn: () => policyApi.get(id) });
  const policy = query.data;
  return (
    <Drawer open width={760} title={policy ? `Chính sách ${policy.documentNo}` : ''} onClose={onClose}>
      {policy && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <PolicyStatusTag status={policy.status} />
            <DocumentActions
              actions={policyActions(policy.status, can)}
              onAction={async (action, body, key) => {
                await policyApi.act(id, action, body, key);
                void queryClient.invalidateQueries({ queryKey: ['stock-issue-policy', id] });
                void queryClient.invalidateQueries({ queryKey: ['stock-issue-policies'] });
              }}
            />
          </Space>
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="Kho">{warehouses.codeOf(policy.warehouseId)}</Descriptions.Item>
            <Descriptions.Item label="Hàng hóa">
              {policy.productId ? `#${policy.productId}` : 'Mọi hàng'}
            </Descriptions.Item>
            <Descriptions.Item label="Hạn mức">{formatNumber(policy.maxQuantity)}</Descriptions.Item>
            <Descriptions.Item label="Đã dùng">{formatNumber(policy.usedQuantity)}</Descriptions.Item>
            <Descriptions.Item label="Chứng từ đã dùng">
              {policy.usedByDocumentId ? (
                <Link to={`/inventory/documents/${policy.usedByDocumentId}`}>Xem chứng từ xuất</Link>
              ) : (
                'Chưa dùng (mỗi chính sách dùng cho một chứng từ)'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Hiệu lực">
              {formatDate(policy.validFrom)} - {formatDate(policy.validTo)}
            </Descriptions.Item>
            <Descriptions.Item label="Lý do">{policy.reason}</Descriptions.Item>
          </Descriptions>
          <ApprovalHistory requests={policy.approvals} />
        </>
      )}
    </Drawer>
  );
}

/** Chính sách xuất vượt tồn khả dụng (BR-GEN-31, D-10): chỉ có hiệu lực sau khi kích hoạt; tồn thực tế vẫn không âm. */
export function StockIssuePoliciesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<PolicyStatus>();
  const [creating, setCreating] = useState(false);
  const openId = params.get('id') ? Number(params.get('id')) : null;
  const { tableProps } = useServerTable('stock-issue-policies', policyApi.list, { companyId, status });

  const create = async (values: PolicyForm, key: string) => {
    const { validRange, ...rest } = values;
    const created = await policyApi.create(
      {
        ...rest,
        validFrom: validRange[0].format('YYYY-MM-DD'),
        validTo: validRange[1].format('YYYY-MM-DD'),
      },
      key,
    );
    message.success(`Đã lập ${created.documentNo}.`);
    setParams({ id: String(created.id) });
  };

  const columns: TableColumnsType<StockIssuePolicy> = [
    {
      title: 'Số',
      dataIndex: 'documentNo',
      sorter: true,
      render: (no: string, row) => <a onClick={() => setParams({ id: String(row.id) })}>{no}</a>,
    },
    { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.codeOf(id) },
    { title: 'Hạn mức', dataIndex: 'maxQuantity', align: 'right', render: (v: number) => formatNumber(v) },
    { title: 'Đã dùng', dataIndex: 'usedQuantity', align: 'right', render: (v: number) => formatNumber(v) },
    { title: 'Từ', dataIndex: 'validFrom', sorter: true, render: formatDate },
    { title: 'Đến', dataIndex: 'validTo', render: formatDate },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (v: PolicyStatus) => <PolicyStatusTag status={v} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4}>Chính sách xuất vượt tồn khả dụng</Typography.Title>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={STATUSES.map((value) => ({ value, label: policyStatusLabel(value) }))}
        />
        {can('stock-policy:create') && (
          <Button type="primary" onClick={() => setCreating(true)}>
            Lập chính sách
          </Button>
        )}
      </Space>
      <Table<StockIssuePolicy> {...tableProps} columns={columns} scroll={{ x: 800 }} />
      {openId !== null && <PolicyDrawer id={openId} onClose={() => setParams({})} />}
      <FormModal<PolicyForm>
        open={creating}
        title="Lập chính sách xuất vượt tồn khả dụng"
        initialValues={{ validRange: [dayjs(), dayjs().add(7, 'day')] }}
        fieldNames={['warehouseId', 'productId', 'maxQuantity', 'validRange', 'reason']}
        onSubmit={create}
        onClose={() => setCreating(false)}
      >
        <Form.Item name="warehouseId" label="Kho" rules={[{ required: true, message: 'Chọn kho' }]}>
          <Select showSearch optionFilterProp="label" options={warehouses.options} />
        </Form.Item>
        <Form.Item name="productId" label="Hàng hóa" extra="Để trống: áp dụng cho mọi hàng của kho.">
          <ProductSelect allowClear />
        </Form.Item>
        <Form.Item name="maxQuantity" label="Số lượng tối đa được xuất vượt" rules={[{ required: true }]}>
          <DecimalInput min={0.001} />
        </Form.Item>
        <Form.Item name="validRange" label="Thời hạn" rules={[{ required: true }]}>
          <DatePicker.RangePicker format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Lý do"
          rules={[{ required: true, whitespace: true, message: 'Nhập lý do' }]}
        >
          <Input.TextArea rows={3} maxLength={1000} />
        </Form.Item>
      </FormModal>
    </Card>
  );
}
