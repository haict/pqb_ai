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
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { RemoteSelect } from '../../masterdata/components/RemoteSelect';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { recallApi, stockApi } from '../api';
import { DocumentActions } from '../components/DocumentActions';
import { formatDate, formatNumber, statusLabel } from '../labels';
import { DocumentStatusTag } from '../components/StatusTags';
import type { Lot, Recall, RecallCustomer, RecallStatus, Serial } from '../types';
import { BRANCH_VISIBLE } from '../../../shared/branch';

const STATUSES: RecallStatus[] = ['DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED'];

interface RecallForm {
  branchId: number;
  recallDate: Dayjs;
  productId: number;
  lotId?: number;
  serialId?: number;
  reason: string;
}

/** Không qua duyệt (D-61): người có quyền recall:manage kích hoạt từ nháp; phiếu chờ duyệt / bị từ chối cũ chỉ còn hủy. */
function recallActions(status: RecallStatus, can: (permission: string) => boolean): string[] {
  const cancel = can('recall:create') ? ['cancel'] : [];
  const manage = can('recall:manage');
  const actions: Record<RecallStatus, string[]> = {
    DRAFT: [...(manage ? ['activate'] : []), ...cancel],
    REJECTED: cancel,
    SUBMITTED: cancel,
    ACTIVE: manage ? ['close'] : [],
    CLOSED: [],
    CANCELLED: [],
  };
  return actions[status];
}

function RecallDrawer({ id, onClose }: { id: number; onClose(): void }) {
  const can = useCan();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['recall', id], queryFn: () => recallApi.get(id) });
  const recall = query.data;
  const columns: TableColumnsType<RecallCustomer> = [
    { title: 'Khách hàng', render: (_, row) => `${row.partnerCode ?? ''} - ${row.partnerName ?? ''}` },
    {
      title: 'Đã giao',
      dataIndex: 'deliveredQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    {
      title: 'Đã thu hồi',
      dataIndex: 'recoveredQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
  ];
  return (
    <Drawer open width={860} title={recall ? `Phiếu thu hồi ${recall.documentNo}` : ''} onClose={onClose}>
      {recall && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={recall.status} />
            <DocumentActions
              actions={recallActions(recall.status, can)}
              onAction={async (action, body, key) => {
                await recallApi.act(id, action, body, key);
                void queryClient.invalidateQueries({ queryKey: ['recall', id] });
                void queryClient.invalidateQueries({ queryKey: ['recalls'] });
              }}
            />
          </Space>
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="Hàng hóa">{`${recall.productCode ?? ''} - ${recall.productName ?? ''}`}</Descriptions.Item>
            <Descriptions.Item label="Ngày">{formatDate(recall.recallDate)}</Descriptions.Item>
            <Descriptions.Item label="Lô">{recall.lotNo}</Descriptions.Item>
            <Descriptions.Item label="Serial">{recall.serialNo}</Descriptions.Item>
            <Descriptions.Item label="Lý do" span={2}>
              {recall.reason}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            Khách đã nhận và tiến độ thu hồi
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            Hàng thu hồi về nhập bằng phiếu nhập kho, nghiệp vụ "Nhập hàng thu hồi từ khách".
          </Typography.Paragraph>
          <Table<RecallCustomer>
            rowKey="partnerId"
            size="small"
            pagination={false}
            dataSource={recall.customers}
            columns={columns}
          />
          <ApprovalHistory requests={recall.approvals} />
        </>
      )}
    </Drawer>
  );
}

function RecallFormFields({ companyId }: { companyId: number }) {
  const branches = useMyBranches();
  const productId = Form.useWatch<number | undefined>('productId');
  return (
    <>
      <Form.Item
        name="branchId"
        label="Chi nhánh lập"
        rules={[{ required: true, message: 'Chọn chi nhánh' }]}
        hidden={!BRANCH_VISIBLE}
      >
        <Select options={branches.options} />
      </Form.Item>
      <Form.Item name="recallDate" label="Ngày thu hồi" rules={[{ required: true }]}>
        <DatePicker format="DD/MM/YYYY" />
      </Form.Item>
      <Form.Item name="productId" label="Hàng hóa" rules={[{ required: true, message: 'Chọn hàng' }]}>
        <ProductSelect />
      </Form.Item>
      {productId && (
        <>
          <Form.Item name="lotId" label="Lô">
            <RemoteSelect<Lot>
              allowClear
              queryKey={`recall-lots-${productId}`}
              fetch={(keyword) => stockApi.lots({ page: 0, size: 30, companyId, productId, keyword })}
              toOption={(lot) => ({ value: lot.id, label: lot.lotNo })}
            />
          </Form.Item>
          <Form.Item name="serialId" label="Serial">
            <RemoteSelect<Serial>
              allowClear
              queryKey={`recall-serials-${productId}`}
              fetch={(keyword) => stockApi.serials({ page: 0, size: 30, companyId, productId, keyword })}
              toOption={(serial) => ({ value: serial.id, label: serial.serialNo })}
            />
          </Form.Item>
        </>
      )}
      <Form.Item
        name="reason"
        label="Lý do thu hồi"
        rules={[{ required: true, whitespace: true, message: 'Nhập lý do' }]}
      >
        <Input.TextArea rows={3} maxLength={1000} />
      </Form.Item>
    </>
  );
}

/** Thu hồi lô / serial (BR-INV-42). */
export function RecallsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<RecallStatus>();
  const [creating, setCreating] = useState(false);
  const openId = params.get('id') ? Number(params.get('id')) : null;
  const { tableProps } = useServerTable('recalls', recallApi.list, { companyId, status });

  const create = async (values: RecallForm, key: string) => {
    const created = await recallApi.create(
      { ...values, recallDate: values.recallDate.format('YYYY-MM-DD') },
      key,
    );
    message.success(`Đã lập ${created.documentNo}.`);
    setParams({ id: String(created.id) });
  };

  const columns: TableColumnsType<Recall> = [
    {
      title: 'Số phiếu',
      dataIndex: 'documentNo',
      sorter: true,
      render: (no: string, row) => <a onClick={() => setParams({ id: String(row.id) })}>{no}</a>,
    },
    { title: 'Ngày', dataIndex: 'recallDate', sorter: true, render: formatDate },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Serial', dataIndex: 'serialNo' },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (v: RecallStatus) => <DocumentStatusTag status={v} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4}>Thu hồi sản phẩm</Typography.Title>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
        />
        {can('recall:create') && (
          <Button type="primary" onClick={() => setCreating(true)}>
            Lập phiếu thu hồi
          </Button>
        )}
      </Space>
      <Table<Recall> {...tableProps} columns={columns} scroll={{ x: 800 }} />
      {openId !== null && <RecallDrawer id={openId} onClose={() => setParams({})} />}
      <FormModal<RecallForm>
        open={creating}
        title="Lập phiếu thu hồi"
        initialValues={{ branchId: branches.defaultId, recallDate: dayjs() }}
        fieldNames={['branchId', 'recallDate', 'productId', 'lotId', 'serialId', 'reason']}
        onSubmit={create}
        onClose={() => setCreating(false)}
      >
        <RecallFormFields companyId={companyId} />
      </FormModal>
    </Card>
  );
}
