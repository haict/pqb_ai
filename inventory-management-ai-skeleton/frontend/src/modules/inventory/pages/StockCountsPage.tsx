import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
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
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/apiError';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { DecimalInput } from '../../../shared/components/DecimalInput';
import { FormModal } from '../../../shared/components/FormModal';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { stockCountApi } from '../api';
import { DocumentActions } from '../components/DocumentActions';
import { formatDate, formatNumber, statusLabel } from '../labels';
import { DocumentStatusTag, QualityTag } from '../components/StatusTags';
import type { StockCount, StockCountLine, StockCountStatus } from '../types';

const STATUSES: StockCountStatus[] = ['DRAFT', 'COUNTING', 'SUBMITTED', 'APPROVED', 'POSTED', 'CANCELLED'];

/** Hành động theo trạng thái và quyền (chỉ ẩn/hiện; server kiểm tra lại). */
function countActions(status: StockCountStatus, can: (permission: string) => boolean): string[] {
  const create = can('stock-count:create');
  const actions: Record<StockCountStatus, string[]> = {
    DRAFT: create ? ['start', 'cancel'] : [],
    COUNTING: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(can('stock-count:approve') ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(can('stock-count:post') ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: [],
    CANCELLED: [],
  };
  return actions[status];
}

function CountDrawer({ id, onClose }: { id: number; onClose(): void }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const warehouses = useWarehouses();
  const [counted, setCounted] = useState<Record<number, number | null>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const query = useQuery({ queryKey: ['stock-count', id], queryFn: () => stockCountApi.get(id) });
  const count = query.data;
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['stock-count', id] });
    void queryClient.invalidateQueries({ queryKey: ['stock-counts'] });
  };

  const saveCounts = async () => {
    const lines = Object.entries(counted)
      .filter(([, value]) => value !== null && value !== undefined)
      .map(([lineId, countedQuantity]) => ({ lineId: Number(lineId), countedQuantity }));
    if (lines.length === 0) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await stockCountApi.count(id, lines);
      setCounted({});
      message.success('Đã lưu số đếm.');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Đã xảy ra lỗi không xác định.');
    } finally {
      setSaving(false);
    }
  };

  const editable = count?.status === 'COUNTING' && can('stock-count:create');
  const columns: TableColumnsType<StockCountLine> = [
    { title: 'Hàng hóa', render: (_, line) => `${line.productCode ?? ''} - ${line.productName ?? ''}` },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', render: formatDate },
    { title: 'Serial', dataIndex: 'serialNo' },
    { title: 'Trạng thái CL', dataIndex: 'qualityStatus', render: (v) => <QualityTag status={v} /> },
    {
      title: 'Sổ sách',
      dataIndex: 'snapshotQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    {
      title: 'Thực tế',
      dataIndex: 'countedQuantity',
      align: 'right',
      render: (value: number | null, line) =>
        editable ? (
          <DecimalInput
            min={0}
            value={counted[line.id] ?? value}
            onChange={(next) => setCounted((current) => ({ ...current, [line.id]: next }))}
          />
        ) : (
          formatNumber(value)
        ),
    },
    {
      title: 'Chênh lệch',
      dataIndex: 'difference',
      align: 'right',
      render: (v: number | null) => formatNumber(v),
    },
  ];

  return (
    <Drawer open width={1000} title={count ? `Phiếu kiểm kê ${count.documentNo}` : ''} onClose={onClose}>
      {count && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={count.status} />
            <DocumentActions
              actions={countActions(count.status, can)}
              onAction={async (action, body, key) => {
                await stockCountApi.act(id, action, body, key);
                refresh();
              }}
            />
          </Space>
          <Descriptions size="small" column={{ xs: 1, md: 3 }}>
            <Descriptions.Item label="Kho">{warehouses.codeOf(count.warehouseId)}</Descriptions.Item>
            <Descriptions.Item label="Ngày kiểm kê">{formatDate(count.countDate)}</Descriptions.Item>
            <Descriptions.Item label="Ghi chú">{count.note}</Descriptions.Item>
            {count.adjustmentDocumentId && (
              <Descriptions.Item label="Phiếu điều chỉnh">
                <Link to={`/inventory/documents/${count.adjustmentDocumentId}`}>Xem phiếu điều chỉnh</Link>
              </Descriptions.Item>
            )}
            {count.cancelReason && (
              <Descriptions.Item label="Lý do hủy">{count.cancelReason}</Descriptions.Item>
            )}
          </Descriptions>
          {count.status === 'COUNTING' && (
            <Alert
              type="warning"
              showIcon
              style={{ margin: '12px 0' }}
              message="Kho đang kiểm kê: mọi chứng từ nhập, xuất của kho bị chặn đến khi ghi sổ hoặc hủy phiếu (D-11)."
            />
          )}
          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
          <Table<StockCountLine>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={count.lines}
            columns={columns}
            scroll={{ x: 900 }}
          />
          {editable && (
            <Button type="primary" style={{ marginTop: 12 }} loading={saving} onClick={saveCounts}>
              Lưu số đếm
            </Button>
          )}
          <Typography.Title level={5} style={{ marginTop: 24 }}>
            Lịch sử duyệt
          </Typography.Title>
          <ApprovalHistory requests={count.approvals} />
        </>
      )}
    </Drawer>
  );
}

/** Kiểm kê kho (BR-INV-31, D-11). */
export function StockCountsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<StockCountStatus>();
  const [creating, setCreating] = useState(false);
  const openId = params.get('id') ? Number(params.get('id')) : null;
  const { tableProps } = useServerTable('stock-counts', stockCountApi.list, { companyId, status });

  const create = async (values: { warehouseId: number; countDate: Dayjs; note?: string }, key: string) => {
    const created = await stockCountApi.create(
      { ...values, countDate: values.countDate.format('YYYY-MM-DD') },
      key,
    );
    message.success(`Đã lập ${created.documentNo}.`);
    setParams({ id: String(created.id) });
  };

  const columns: TableColumnsType<StockCount> = [
    {
      title: 'Số phiếu',
      dataIndex: 'documentNo',
      sorter: true,
      render: (no: string, row) => <a onClick={() => setParams({ id: String(row.id) })}>{no}</a>,
    },
    { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.codeOf(id) },
    { title: 'Ngày kiểm kê', dataIndex: 'countDate', sorter: true, render: formatDate },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (v: StockCountStatus) => <DocumentStatusTag status={v} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4}>Kiểm kê kho</Typography.Title>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
        />
        {can('stock-count:create') && (
          <Button type="primary" onClick={() => setCreating(true)}>
            Lập phiếu kiểm kê
          </Button>
        )}
      </Space>
      <Table<StockCount> {...tableProps} columns={columns} />
      {openId !== null && <CountDrawer id={openId} onClose={() => setParams({})} />}
      <FormModal<{ warehouseId: number; countDate: Dayjs; note?: string }>
        open={creating}
        title="Lập phiếu kiểm kê"
        initialValues={{ countDate: dayjs() }}
        fieldNames={['warehouseId', 'countDate', 'note']}
        onSubmit={create}
        onClose={() => setCreating(false)}
      >
        <Form.Item name="warehouseId" label="Kho" rules={[{ required: true, message: 'Chọn kho' }]}>
          <Select showSearch optionFilterProp="label" options={warehouses.options} />
        </Form.Item>
        <Form.Item name="countDate" label="Ngày kiểm kê" rules={[{ required: true }]}>
          <DatePicker format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú">
          <Input.TextArea rows={2} maxLength={1000} />
        </Form.Item>
      </FormModal>
    </Card>
  );
}
