import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Table,
  Typography,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { formatMoney } from '../../accounting/labels';
import { moneyInputProps } from '../../finance/components/debtInputs';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { costAllocationApi, purchaseOrderApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { SourceJournal } from '../components/SourceJournal';
import { ALLOCATION_METHODS, postingActions, PURCHASE_STATUSES } from '../labels';
import type {
  CostAllocationMethod,
  CostAllocationSource,
  CostAllocationTarget,
  PurchaseCostAllocation,
  PurchaseDocumentStatus,
} from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';

interface AllocationForm {
  branchId: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  method: CostAllocationMethod;
  description?: string;
  sources: { orderLineId?: number; amount?: number }[];
  orderIds: number[];
}

const FIELDS = ['branchId', 'documentDate', 'postingDate', 'method', 'description', 'sources', 'orderIds'];

const sourceColumns: TableColumnsType<CostAllocationSource> = [
  { title: '#', dataIndex: 'lineNo', width: 45 },
  { title: 'Đơn mua', render: (_, row) => `${row.orderNo ?? ''} (${row.invoiceLabel ?? ''})` },
  { title: 'Nhà cung cấp', dataIndex: 'supplierName', width: 200 },
  { title: 'Dịch vụ', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
  { title: 'Số tiền phân bổ', dataIndex: 'amount', width: 140, align: 'right', render: formatMoney },
];

const targetColumns: TableColumnsType<CostAllocationTarget> = [
  { title: '#', dataIndex: 'lineNo', width: 45 },
  { title: 'Đơn mua', dataIndex: 'orderNo', width: 170 },
  { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
  {
    title: 'SL cơ sở',
    dataIndex: 'baseQuantity',
    width: 100,
    align: 'right',
    render: (value: number) => formatNumber(value),
  },
  { title: 'Giá trị nhập', dataIndex: 'basisValue', width: 130, align: 'right', render: formatMoney },
  { title: 'Được phân bổ', dataIndex: 'allocatedAmount', width: 130, align: 'right', render: formatMoney },
];

/**
 * Phân bổ chi phí mua hàng (BR-PUR-12, 16): chi phí dịch vụ (vận chuyển, bảo hiểm...) của đơn mua đã ghi sổ phân bổ
 * vào hàng hóa của các đơn mua đã ghi sổ theo giá trị hoặc số lượng; ghi sổ điều chỉnh giá trị nhập (giá vốn tính lại ở
 * Giai đoạn 8).
 */
export function CostAllocationsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseCostAllocation | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-cost-allocations', costAllocationApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: AllocationForm, key: string) => {
    const input = {
      branchId: values.branchId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      method: values.method,
      description: values.description,
      sources: values.sources ?? [],
      orderIds: values.orderIds ?? [],
    };
    if (editing === 'new') {
      const created = await costAllocationApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await costAllocationApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu phân bổ.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-cost-allocation', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-cost-allocations'] });
  };

  const initialValues: Partial<AllocationForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          method: editing.method,
          description: editing.description ?? undefined,
          sources: editing.sources.map((source) => ({
            orderLineId: source.orderLineId,
            amount: source.amount,
          })),
          orderIds: editing.orderIds,
        }
      : {
          branchId: branches.defaultId,
          documentDate: dayjs(),
          method: 'VALUE',
          sources: [{}],
          orderIds: [],
        };

  const columns: TableColumnsType<PurchaseCostAllocation> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    {
      title: 'Cách phân bổ',
      dataIndex: 'method',
      width: 160,
      render: (value: CostAllocationMethod) => ALLOCATION_METHODS[value],
    },
    { title: 'Diễn giải', dataIndex: 'description' },
    { title: 'Số tiền', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: PurchaseDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Phân bổ chi phí mua hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 160 }}
            value={status}
            onChange={setStatus}
            options={PURCHASE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập phân bổ"
        onCreate={can('purchase-cost-allocation:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseCostAllocation> {...tableProps} size="small" columns={columns} scroll={{ x: 900 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseCostAllocation>
          id={selected}
          queryKey="purchase-cost-allocation"
          listKeys={['purchase-cost-allocations']}
          load={costAllocationApi.get}
          act={costAllocationApi.act}
          permission="purchase-cost-allocation"
          actions={(document) => postingActions(document.status, 'purchase-cost-allocation', can)}
          title={(document) => `Phân bổ chi phí mua ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Cách phân bổ">
                  {ALLOCATION_METHODS[document.method]}
                </Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Tổng chi phí">
                  <strong>{formatMoney(document.amount)}</strong>
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={3}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Typography.Title level={5}>Chi phí</Typography.Title>
              <Table<CostAllocationSource>
                rowKey="orderLineId"
                size="small"
                pagination={false}
                columns={sourceColumns}
                dataSource={document.sources}
              />
              <Typography.Title level={5} style={{ marginTop: 16 }}>
                Phân bổ vào hàng hóa của đơn mua
              </Typography.Title>
              <Table<CostAllocationTarget>
                rowKey="orderLineId"
                size="small"
                pagination={false}
                columns={targetColumns}
                dataSource={document.targets}
              />
              <SourceJournal sourceType="PBC" sourceId={document.id} status={document.status} />
            </>
          )}
        </PurchaseDocumentDrawer>
      )}
      <FormModal<AllocationForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập phân bổ chi phí mua' : 'Sửa phân bổ chi phí mua'}
        width={1000}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <AllocationFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />
        )}
      </FormModal>
    </Card>
  );
}

function AllocationFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<AllocationForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const companyId = useCompanyId();
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const costLines = useQuery({
    queryKey: ['purchase-cost-allocations', 'cost-lines', branchId],
    queryFn: () => costAllocationApi.costLines({ branchId: branchId as number }),
    enabled: typeof branchId === 'number',
  });
  const orders = useQuery({
    queryKey: ['purchase-orders', 'posted', branchId],
    queryFn: () => purchaseOrderApi.list({ page: 0, size: 100, companyId, branchId, status: 'POSTED' }),
    enabled: typeof branchId === 'number',
  });
  const lines = costLines.data ?? [];
  return (
    <>
      <Row gutter={12}>
        <BranchCol xs={24} md={6}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select
              disabled={!isNew}
              options={branchOptions}
              onChange={() => form.setFieldsValue({ sources: [{}], orderIds: [] })}
            />
          </Form.Item>
        </BranchCol>
        <Col xs={12} md={5}>
          <Form.Item
            name="documentDate"
            label="Ngày chứng từ"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={5}>
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="method" label="Cách phân bổ" rules={[{ required: true }]}>
            <Select
              options={(Object.keys(ALLOCATION_METHODS) as CostAllocationMethod[]).map((value) => ({
                value,
                label: ALLOCATION_METHODS[value],
              }))}
            />
          </Form.Item>
        </Col>
        <Col xs={24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Typography.Title level={5}>Chi phí (dòng dịch vụ của đơn mua đã ghi sổ)</Typography.Title>
      <Form.List name="sources">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={8} key={field.key}>
                <Col xs={24} md={16}>
                  <Form.Item
                    name={[field.name, 'orderLineId']}
                    rules={[{ required: true, message: 'Chọn dòng chi phí' }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      loading={costLines.isLoading}
                      placeholder="Dòng chi phí"
                      options={lines.map((line) => ({
                        value: line.orderLineId,
                        label: `${line.orderNo ?? ''} · ${line.supplierName ?? ''} · ${
                          line.productName ?? ''
                        } (còn ${formatMoney(line.remainingAmount ?? 0)})`,
                      }))}
                      onChange={(value: number) => {
                        const line = lines.find((candidate) => candidate.orderLineId === value);
                        form.setFieldValue(
                          ['sources', field.name, 'amount'],
                          line?.remainingAmount ?? undefined,
                        );
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col xs={20} md={7}>
                  <Form.Item
                    name={[field.name, 'amount']}
                    rules={[{ required: true, message: 'Nhập số tiền' }]}
                  >
                    <InputNumber<number> min={1} placeholder="Số tiền" {...moneyInputProps} />
                  </Form.Item>
                </Col>
                <Col xs={4} md={1}>
                  <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                </Col>
              </Row>
            ))}
            <Space style={{ marginBottom: 12 }}>
              <Button icon={<PlusOutlined />} onClick={() => add({})}>
                Thêm dòng chi phí
              </Button>
            </Space>
          </>
        )}
      </Form.List>
      <Form.Item
        name="orderIds"
        label="Đơn mua nhận phân bổ (đã ghi sổ, có hàng hóa)"
        rules={[{ required: true, message: 'Chọn đơn mua' }]}
      >
        <Select
          mode="multiple"
          showSearch
          optionFilterProp="label"
          loading={orders.isLoading}
          options={(orders.data?.items ?? [])
            .filter((order) => order.inventoryDocumentId)
            .map((order) => ({
              value: order.id,
              label: `${order.documentNo} · ${order.supplierName ?? ''} · ${formatDate(order.postingDate)}`,
            }))}
        />
      </Form.Item>
    </>
  );
}
