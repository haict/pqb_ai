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
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCatalog, useCompanyId, useTaxRates, useWarehouses } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { purchaseOrderApi, requisitionApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { useProductUnits } from '../components/productUnits';
import { PURCHASE_STATUSES } from '../labels';
import type { PurchaseDocumentStatus, PurchaseOrder, PurchaseOrderLine } from '../types';

interface LineForm {
  productId?: number;
  productLabel?: string;
  unitId?: number;
  quantity?: number;
  unitPrice?: number;
  discountPercent?: number;
  taxRateId?: number;
  requisitionLineId?: number;
  expectedDate?: Dayjs;
  note?: string;
}

interface OrderForm {
  branchId: number;
  documentDate: Dayjs;
  supplierId?: number;
  supplierLabel?: string;
  warehouseId?: number;
  expectedDate?: Dayjs;
  paymentTermDays?: number;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'branchId',
  'documentDate',
  'supplierId',
  'warehouseId',
  'expectedDate',
  'paymentTermDays',
  'description',
  'lines',
];

const quantity = (value: number) => formatNumber(value);

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseOrderLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45, fixed: 'left' },
    {
      title: 'Hàng hóa',
      width: 220,
      fixed: 'left',
      render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}`,
    },
    {
      title: 'Số lượng',
      width: 120,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 120, align: 'right', render: formatMoney },
    { title: 'CK %', dataIndex: 'discountPercent', width: 70, align: 'right', render: quantity },
    { title: 'Thành tiền', dataIndex: 'amount', width: 130, align: 'right', render: formatMoney },
    { title: 'Thuế %', dataIndex: 'taxRate', width: 70, align: 'right', render: quantity },
    { title: 'Tiền thuế', dataIndex: 'taxAmount', width: 120, align: 'right', render: formatMoney },
    {
      title: 'Đặt (ĐVT cơ sở)',
      width: 130,
      align: 'right',
      render: (_, row) => `${formatNumber(row.baseQuantity)} ${unitName(row.baseUnitId)}`,
    },
    { title: 'Đã nhận', dataIndex: 'receivedQuantity', width: 90, align: 'right', render: quantity },
    { title: 'Đã nhập', dataIndex: 'stockedQuantity', width: 90, align: 'right', render: quantity },
    { title: 'Lỗi', dataIndex: 'rejectedQuantity', width: 70, align: 'right', render: quantity },
    { title: 'Đã nhận HĐ', dataIndex: 'invoicedQuantity', width: 100, align: 'right', render: quantity },
    { title: 'Đã đóng', dataIndex: 'closedQuantity', width: 90, align: 'right', render: quantity },
    { title: 'Còn thiếu', dataIndex: 'outstandingQuantity', width: 90, align: 'right', render: quantity },
    { title: 'Ngày giao', dataIndex: 'expectedDate', width: 110, render: formatDate },
    { title: 'Ghi chú', dataIndex: 'note', width: 160 },
  ];
}

/**
 * Đơn mua hàng (DMH, BR-PUR-04, BR-PUR-05): lập (có thể theo đề nghị mua), duyệt theo ngưỡng, theo dõi tiến độ từng
 * dòng, đóng phần còn lại. Đơn đã duyệt cộng vào hàng đang về của kho nhận.
 */
export function PurchaseOrdersPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseOrder | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-orders', purchaseOrderApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: OrderForm, key: string) => {
    const input = {
      branchId: values.branchId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      supplierId: values.supplierId,
      warehouseId: values.warehouseId,
      expectedDate: values.expectedDate ? values.expectedDate.format('YYYY-MM-DD') : null,
      paymentTermDays: values.paymentTermDays ?? null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        productId: line.productId,
        unitId: line.unitId,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        discountPercent: line.discountPercent ?? 0,
        taxRateId: line.taxRateId ?? null,
        requisitionLineId: line.requisitionLineId ?? null,
        expectedDate: line.expectedDate ? line.expectedDate.format('YYYY-MM-DD') : null,
        note: line.note,
      })),
    };
    if (editing === 'new') {
      const created = await purchaseOrderApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await purchaseOrderApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu đơn mua.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-order', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
  };

  const initialValues: Partial<OrderForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          documentDate: dayjs(editing.documentDate),
          supplierId: editing.supplierId,
          supplierLabel: `${editing.supplierCode ?? ''} - ${editing.supplierName ?? ''}`,
          warehouseId: editing.warehouseId,
          expectedDate: editing.expectedDate ? dayjs(editing.expectedDate) : undefined,
          paymentTermDays: editing.paymentTermDays ?? undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            productId: line.productId,
            productLabel: `${line.productCode ?? ''} - ${line.productName ?? ''}`,
            unitId: line.unitId,
            quantity: line.quantity,
            unitPrice: line.unitPrice,
            discountPercent: line.discountPercent,
            taxRateId: line.taxRateId ?? undefined,
            requisitionLineId: line.requisitionLineId ?? undefined,
            expectedDate: line.expectedDate ? dayjs(line.expectedDate) : undefined,
            note: line.note ?? undefined,
          })),
        }
      : { branchId: branches.items[0]?.id, documentDate: dayjs(), lines: [{}] };

  const columns: TableColumnsType<PurchaseOrder> = [
    {
      title: 'Số đơn',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    { title: 'Nhà cung cấp', render: (_, row) => `${row.supplierCode ?? ''} - ${row.supplierName ?? ''}` },
    { title: 'Kho nhận', dataIndex: 'warehouseName', width: 170 },
    { title: 'Ngày giao', dataIndex: 'expectedDate', width: 110, render: formatDate },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 150, align: 'right', render: formatMoney },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: PurchaseDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Đơn mua hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số đơn, diễn giải"
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
        createLabel="Lập đơn mua"
        onCreate={can('purchase-order:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseOrder> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseOrder>
          id={selected}
          queryKey="purchase-order"
          listKeys={['purchase-orders', 'purchase-requisitions', 'stock-summaries']}
          load={purchaseOrderApi.get}
          act={purchaseOrderApi.act}
          permission="purchase-order"
          title={(document) => `Đơn mua ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Nhà cung cấp">
                  {`${document.supplierCode ?? ''} - ${document.supplierName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Kho nhận">{document.warehouseName}</Descriptions.Item>
                <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
                <Descriptions.Item label="Ngày đơn">{formatDate(document.documentDate)}</Descriptions.Item>
                <Descriptions.Item label="Ngày giao dự kiến">
                  {formatDate(document.expectedDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Hạn thanh toán">
                  {document.paymentTermDays === null ? '' : `${document.paymentTermDays} ngày`}
                </Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Chiết khấu">
                  {formatMoney(document.discountAmount)}
                </Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng thanh toán">
                  <strong>{formatMoney(document.totalAmount)}</strong> {document.currencyCode}
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={3}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<PurchaseOrderLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 2000 }}
              />
            </>
          )}
        </PurchaseDocumentDrawer>
      )}
      <FormModal<OrderForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập đơn mua hàng' : 'Sửa đơn mua hàng'}
        width={1200}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <OrderFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
    </Card>
  );
}

function OrderFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<OrderForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const warehouses = useWarehouses();
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const warehouseOptions = warehouses.items
    .filter((warehouse) => warehouse.branchId === branchId && warehouse.warehouseType === 'NORMAL')
    .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} - ${warehouse.name}` }));
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={6}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select
              disabled={!isNew}
              options={branchOptions}
              onChange={() => form.setFieldsValue({ warehouseId: undefined })}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item
            name="supplierId"
            label="Nhà cung cấp"
            rules={[{ required: true, message: 'Chọn nhà cung cấp' }]}
          >
            <PartnerSelect role="SUPPLIER" selectedLabel={form.getFieldValue('supplierLabel')} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item name="warehouseId" label="Kho nhận" rules={[{ required: true, message: 'Chọn kho' }]}>
            <Select options={warehouseOptions} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="documentDate" label="Ngày đơn" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="expectedDate" label="Ngày giao dự kiến">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="paymentTermDays" label="Hạn thanh toán (ngày)" extra="Trống: theo nhà cung cấp">
            <InputNumber<number> min={0} max={3650} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={16}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <OrderLineFields
                key={field.key}
                form={form}
                name={field.name}
                branchId={branchId}
                removable={fields.length > 1}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}

function OrderLineFields({
  form,
  name,
  branchId,
  removable,
  onRemove,
}: {
  form: FormInstance<OrderForm>;
  name: number;
  branchId: number | undefined;
  removable: boolean;
  onRemove(): void;
}) {
  const taxRates = useTaxRates();
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const { options } = useProductUnits(productId);
  const openLines = useQuery({
    queryKey: ['purchase-requisitions', 'open-lines', branchId, productId],
    queryFn: () => requisitionApi.openLines({ branchId: branchId as number, productId }),
    enabled: typeof branchId === 'number' && typeof productId === 'number',
  });
  const requisitionOptions = (openLines.data ?? []).map((line) => ({
    value: line.lineId,
    label: `${line.documentNo} dòng ${line.lineNo} - còn ${formatNumber(line.remainingQuantity)}`,
  }));
  const current = form.getFieldValue(['lines', name, 'requisitionLineId']) as number | undefined;
  if (current && !requisitionOptions.some((option) => option.value === current)) {
    requisitionOptions.push({ value: current, label: `Dòng đề nghị #${current}` });
  }
  return (
    <Card size="small" style={{ marginBottom: 8 }}>
      <Row gutter={8}>
        <Col xs={24} md={7}>
          <Form.Item
            name={[name, 'productId']}
            label="Hàng hóa"
            rules={[{ required: true, message: 'Chọn hàng' }]}
          >
            <ProductSelect
              selectedLabel={form.getFieldValue(['lines', name, 'productLabel'])}
              onProduct={(product) => {
                if (product) {
                  form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId);
                  form.setFieldValue(['lines', name, 'taxRateId'], product.defaultTaxRateId ?? undefined);
                  form.setFieldValue(['lines', name, 'requisitionLineId'], undefined);
                }
              }}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item name={[name, 'unitId']} label="Đơn vị" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
            <Select options={options} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item
            name={[name, 'quantity']}
            label="Số lượng"
            rules={[{ required: true, message: 'Nhập số lượng' }]}
          >
            <InputNumber min={0.001} precision={3} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name={[name, 'unitPrice']}
            label="Đơn giá"
            rules={[{ required: true, message: 'Nhập đơn giá' }]}
          >
            <InputNumber<number> min={0} {...moneyInputProps} />
          </Form.Item>
        </Col>
        <Col xs={12} md={2}>
          <Form.Item name={[name, 'discountPercent']} label="CK %">
            <InputNumber min={0} max={100} precision={4} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name={[name, 'taxRateId']} label="Thuế suất">
            <Select allowClear options={taxRates.options} />
          </Form.Item>
        </Col>
        <Col xs={2} md={1}>
          <Button
            style={{ marginTop: 30 }}
            icon={<DeleteOutlined />}
            disabled={!removable}
            onClick={onRemove}
          />
        </Col>
        <Col xs={24} md={9}>
          <Form.Item
            name={[name, 'requisitionLineId']}
            label="Theo đề nghị mua"
            extra="Đề nghị đã duyệt của chi nhánh, cùng mặt hàng; số lượng tính theo đơn vị cơ sở"
          >
            <Select allowClear options={requisitionOptions} loading={openLines.isLoading} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name={[name, 'expectedDate']} label="Ngày giao dòng">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={11}>
          <Form.Item name={[name, 'note']} label="Ghi chú">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
    </Card>
  );
}
