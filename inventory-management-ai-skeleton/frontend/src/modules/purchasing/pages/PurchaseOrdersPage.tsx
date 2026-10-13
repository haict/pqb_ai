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
import { DecimalInput } from '../../../shared/components/DecimalInput';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { formatMoney } from '../../accounting/labels';
import { moneyInputProps } from '../../finance/components/debtInputs';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { partnerApi } from '../../masterdata/api';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCatalog, useCompanyId, useTaxRates, useWarehouses } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { purchaseOrderApi } from '../api';
import { PrintButton } from '../../../shared/components/PrintButton';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { StockInSlipPrint } from '../components/StockInSlipPrint';
import { SourceJournal } from '../components/SourceJournal';
import { useProductUnits } from '../components/productUnits';
import { PURCHASE_STATUSES } from '../labels';
import type { PurchaseDocumentStatus, PurchaseOrder, PurchaseOrderLine } from '../types';

interface LineForm {
  productId?: number;
  productLabel?: string;
  serviceLine?: boolean;
  unitId?: number;
  quantity?: number;
  unitPrice?: number;
  discountPercent?: number;
  taxRateId?: number;
  lotNo?: string;
  manufactureDate?: Dayjs;
  expiryDate?: Dayjs;
  serialNumbers?: string[];
  note?: string;
}

interface OrderForm {
  branchId: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  supplierId?: number;
  supplierLabel?: string;
  warehouseId?: number;
  expectedDate?: Dayjs;
  paymentTermDays?: number;
  invoiceSeries?: string;
  invoiceNo?: string;
  invoiceDate?: Dayjs;
  dueDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'branchId',
  'documentDate',
  'postingDate',
  'supplierId',
  'warehouseId',
  'expectedDate',
  'paymentTermDays',
  'invoiceSeries',
  'invoiceNo',
  'invoiceDate',
  'dueDate',
  'description',
  'lines',
];

const ACTION_ORDER = ['submit', 'approve', 'reject', 'post', 'reverse', 'cancel'];
const date = (value?: Dayjs) => (value ? value.format('YYYY-MM-DD') : null);
const quantity = (value: number) => formatNumber(value);

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseOrderLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45, fixed: 'left' },
    {
      title: 'Hàng hóa',
      width: 220,
      fixed: 'left',
      render: (_, row) =>
        `${row.productCode ?? ''} - ${row.productName ?? ''}${row.serviceLine ? ' (dịch vụ)' : ''}`,
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
    { title: 'Lô', dataIndex: 'lotNo', width: 120 },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', width: 110, render: formatDate },
    {
      title: 'Serial',
      width: 180,
      render: (_, row) => row.serialNumbers.join(', '),
    },
    { title: 'Đã trả NCC', dataIndex: 'returnedQuantity', width: 100, align: 'right', render: quantity },
    { title: 'Đã giảm', dataIndex: 'adjustedAmount', width: 120, align: 'right', render: formatMoney },
    { title: 'Đã phân bổ', dataIndex: 'allocatedAmount', width: 120, align: 'right', render: formatMoney },
    { title: 'Ghi chú', dataIndex: 'note', width: 160 },
  ];
}

/**
 * Đơn mua hàng (DMH, BR-PUR-04, D-54): chứng từ duy nhất của nghiệp vụ mua, mang hóa đơn của nhà cung cấp, lô / hạn
 * dùng / serial theo dòng. Duyệt theo ngưỡng (cộng hàng đang về); ghi sổ = nhập kho + công nợ phải trả + bút toán; đảo.
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
      documentDate: date(values.documentDate),
      postingDate: date(values.postingDate),
      supplierId: values.supplierId,
      warehouseId: values.warehouseId,
      expectedDate: date(values.expectedDate),
      paymentTermDays: values.paymentTermDays ?? null,
      invoiceSeries: values.invoiceSeries || null,
      invoiceNo: values.invoiceNo || null,
      invoiceDate: date(values.invoiceDate),
      dueDate: date(values.dueDate),
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        productId: line.productId,
        unitId: line.unitId,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        discountPercent: line.discountPercent ?? 0,
        taxRateId: line.taxRateId ?? null,
        lotNo: line.lotNo || null,
        manufactureDate: date(line.manufactureDate),
        expiryDate: date(line.expiryDate),
        serialNumbers: line.serialNumbers ?? [],
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
          postingDate: dayjs(editing.postingDate),
          supplierId: editing.supplierId,
          supplierLabel: `${editing.supplierCode ?? ''} - ${editing.supplierName ?? ''}`,
          warehouseId: editing.warehouseId,
          expectedDate: editing.expectedDate ? dayjs(editing.expectedDate) : undefined,
          paymentTermDays: editing.paymentTermDays ?? undefined,
          invoiceSeries: editing.invoiceSeries ?? undefined,
          invoiceNo: editing.invoiceNo ?? undefined,
          invoiceDate: editing.invoiceDate ? dayjs(editing.invoiceDate) : undefined,
          dueDate: editing.dueDate ? dayjs(editing.dueDate) : undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            productId: line.productId,
            productLabel: `${line.productCode ?? ''} - ${line.productName ?? ''}`,
            serviceLine: line.serviceLine,
            unitId: line.unitId,
            quantity: line.quantity,
            unitPrice: line.unitPrice,
            discountPercent: line.discountPercent,
            taxRateId: line.taxRateId ?? undefined,
            lotNo: line.lotNo ?? undefined,
            manufactureDate: line.manufactureDate ? dayjs(line.manufactureDate) : undefined,
            expiryDate: line.expiryDate ? dayjs(line.expiryDate) : undefined,
            serialNumbers: line.serialNumbers,
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
    {
      title: 'Hóa đơn',
      width: 160,
      render: (_, row) => (row.invoiceNo ? `${row.invoiceSeries}-${row.invoiceNo}` : ''),
    },
    { title: 'Kho nhận', dataIndex: 'warehouseName', width: 170 },
    { title: 'Phiếu nhập kho', dataIndex: 'inventoryDocumentNo', width: 170 },
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
      <Table<PurchaseOrder> {...tableProps} size="small" columns={columns} scroll={{ x: 1300 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseOrder>
          id={selected}
          queryKey="purchase-order"
          listKeys={['purchase-orders', 'stock-summaries', 'inventory-documents', 'debt-items']}
          load={purchaseOrderApi.get}
          act={purchaseOrderApi.act}
          permission="purchase-order"
          actionOrder={ACTION_ORDER}
          title={(document) => `Đơn mua ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
          extra={(document) =>
            document.status === 'POSTED' && (
              <PrintButton
                label="In phiếu nhập kho"
                title={`Phiếu nhập kho ${document.inventoryDocumentNo ?? ''}`}
              >
                <StockInSlipPrint orderId={document.id} />
              </PrintButton>
            )
          }
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
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Ngày giao dự kiến">
                  {formatDate(document.expectedDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Hóa đơn NCC">
                  {document.invoiceNo
                    ? `${document.invoiceSeries}-${document.invoiceNo} ngày ${formatDate(document.invoiceDate)}`
                    : 'Không có hóa đơn'}
                </Descriptions.Item>
                <Descriptions.Item label="MST người bán">{document.supplierTaxCode}</Descriptions.Item>
                <Descriptions.Item label="Địa chỉ người bán" span={2}>
                  {document.supplierAddress}
                </Descriptions.Item>
                <Descriptions.Item label="Hạn thanh toán">{formatDate(document.dueDate)}</Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Chiết khấu">
                  {formatMoney(document.discountAmount)}
                </Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng thanh toán">
                  <strong>{formatMoney(document.totalAmount)}</strong> {document.currencyCode}
                </Descriptions.Item>
                <Descriptions.Item label="Phiếu nhập kho">{document.inventoryDocumentNo}</Descriptions.Item>
                {document.reverseDate && (
                  <Descriptions.Item label="Ngày đảo">{formatDate(document.reverseDate)}</Descriptions.Item>
                )}
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
              <SourceJournal sourceType="DMH" sourceId={document.id} status={document.status} />
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
  const supplierId = Form.useWatch('supplierId', form) as number | undefined;
  // MST, địa chỉ người bán luôn theo danh mục nhà cung cấp, chỉ hiển thị
  const supplier = useQuery({
    queryKey: ['partners', supplierId],
    queryFn: () => partnerApi.get(supplierId as number),
    enabled: typeof supplierId === 'number',
  }).data;
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
        <Col xs={24} md={4}>
          <Form.Item label="MST người bán" tooltip="Theo danh mục nhà cung cấp">
            <Input readOnly value={supplier?.taxCode ?? ''} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item label="Địa chỉ người bán" tooltip="Theo danh mục nhà cung cấp">
            <Input readOnly value={supplier?.address ?? ''} />
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
          <Form.Item name="postingDate" label="Ngày hạch toán" tooltip="Trống: bằng ngày đơn">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="expectedDate" label="Ngày giao dự kiến">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="paymentTermDays" label="Số ngày nợ" tooltip="Trống: theo nhà cung cấp">
            <InputNumber<number> min={0} max={3650} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="dueDate" label="Hạn thanh toán" tooltip="Trống: ngày hóa đơn + số ngày nợ">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="invoiceSeries" label="Ký hiệu hóa đơn">
            <Input maxLength={20} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="invoiceNo" label="Số hóa đơn">
            <Input maxLength={30} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="invoiceDate" label="Ngày hóa đơn">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Typography.Text type="secondary">
        Hóa đơn của nhà cung cấp không bắt buộc; nhập thì đủ ký hiệu, số, ngày. Hàng nhiều lô: mỗi lô một
        dòng.
      </Typography.Text>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <OrderLineFields
                key={field.key}
                form={form}
                name={field.name}
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
  removable,
  onRemove,
}: {
  form: FormInstance<OrderForm>;
  name: number;
  removable: boolean;
  onRemove(): void;
}) {
  const taxRates = useTaxRates();
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const service = Form.useWatch(['lines', name, 'serviceLine'], form) as boolean | undefined;
  const { product, options } = useProductUnits(productId);
  return (
    <Card size="small" style={{ margin: '8px 0' }}>
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
                  form.setFieldValue(['lines', name, 'serviceLine'], product.productType === 'SERVICE');
                }
              }}
            />
          </Form.Item>
          <Form.Item name={[name, 'serviceLine']} hidden>
            <Input />
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
            <DecimalInput min={0.001} />
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
            <DecimalInput min={0} max={100} fractionDigits={4} />
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
        {!service && product?.lotTracked && (
          <>
            <Col xs={12} md={4}>
              <Form.Item
                name={[name, 'lotNo']}
                label="Số lô"
                preserve={false}
                rules={[{ required: true, message: 'Nhập số lô' }]}
              >
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item name={[name, 'manufactureDate']} label="Ngày sản xuất" preserve={false}>
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item
                name={[name, 'expiryDate']}
                label="Hạn dùng"
                preserve={false}
                rules={[{ required: product.expiryTracked, message: 'Nhập hạn dùng' }]}
              >
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </>
        )}
        {!service && product?.serialTracked && (
          <Col xs={24} md={8}>
            <Form.Item
              name={[name, 'serialNumbers']}
              label="Serial"
              preserve={false}
              tooltip="Gõ serial rồi Enter; số serial = số lượng theo đơn vị cơ sở"
              rules={[{ required: true, message: 'Nhập serial' }]}
            >
              <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
            </Form.Item>
          </Col>
        )}
        <Col xs={24} md={service ? 24 : 6}>
          <Form.Item name={[name, 'note']} label="Ghi chú">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
    </Card>
  );
}
