import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Table,
  Tag,
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
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCatalog, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { SourceJournal } from '../../purchasing/components/SourceJournal';
import { useMyBranches } from '../../system/lookups';
import { salesOrderApi } from '../api';
import { ConfirmDeliveryModal, DeliveryNote, EinvoiceModal } from '../components/OrderModals';
import { StockOutSlipPrint } from '../components/StockOutSlipPrint';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { SalesLinesEditor } from '../components/SalesLineFields';
import { toLineForm, toLineInput, type SalesLineForm } from '../components/salesLines';
import { PrintButton } from '../../../shared/components/PrintButton';
import { SalesLinesTable } from '../components/SalesLinesTable';
import { DELIVERY_STATUSES, DUE_BASES, ORDER_ACTION_ORDER, orderActions, POSTING_STATUSES } from '../labels';
import type {
  DeliveryStatus,
  PaymentDueBasis,
  PaymentSchedule,
  SalesDocumentStatus,
  SalesOrder,
} from '../types';

interface TermForm {
  percent?: number;
  dueBasis?: PaymentDueBasis;
  dueDays?: number;
  description?: string;
}

interface OrderForm {
  branchId: number;
  customerId?: number;
  customerLabel?: string;
  warehouseId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  contactName?: string;
  deliveryAddress?: string;
  receiverName?: string;
  receiverPhone?: string;
  expectedDate?: Dayjs;
  paymentTermDays?: number;
  reserveStock?: boolean;
  description?: string;
  lines: SalesLineForm[];
  paymentTerms: TermForm[];
}

const FIELDS = [
  'branchId',
  'customerId',
  'warehouseId',
  'documentDate',
  'postingDate',
  'contactName',
  'deliveryAddress',
  'receiverName',
  'receiverPhone',
  'expectedDate',
  'paymentTermDays',
  'reserveStock',
  'description',
  'lines',
  'paymentTerms',
];

const dateValue = (value?: Dayjs) => (value ? value.format('YYYY-MM-DD') : null);

const scheduleColumns: TableColumnsType<PaymentSchedule> = [
  { title: 'Đợt', dataIndex: 'installmentNo', width: 50 },
  { title: 'Tỷ lệ %', dataIndex: 'percent', width: 80, align: 'right' },
  { title: 'Phải thu', dataIndex: 'amount', width: 130, align: 'right', render: formatMoney },
  {
    title: 'Mốc',
    render: (_, row) => `${DUE_BASES[row.dueBasis]} + ${row.dueDays} ngày`,
  },
  {
    title: 'Hạn',
    dataIndex: 'dueDate',
    width: 110,
    render: (value: string | null, row) =>
      value ? (
        <span>
          {formatDate(value)} {row.overdue && <Tag color="red">Quá hạn</Tag>}
        </span>
      ) : (
        'Chưa đến mốc'
      ),
  },
  { title: 'Đã thu', dataIndex: 'paidAmount', width: 120, align: 'right', render: formatMoney },
  { title: 'Còn lại', dataIndex: 'remainingAmount', width: 120, align: 'right', render: formatMoney },
];

/**
 * Đơn bán (BR-SAL-10..14, 21..23, 30, 31, 50, D-54): chứng từ duy nhất của nghiệp vụ bán. Giá theo bảng giá, duyệt
 * giá / hạn mức tín dụng, tồn khả dụng, giữ / nhả hàng; ghi sổ = xuất kho (FEFO / lô chỉ định) + doanh thu + công nợ
 * phải thu; số hóa đơn điện tử; theo dõi giao hàng, in biên bản giao nhận; đảo (kể cả giao thất bại); lịch thu tiền.
 */
export function SalesOrdersPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SalesDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<SalesOrder | 'new' | null>(null);
  const [confirming, setConfirming] = useState<SalesOrder | null>(null);
  const [einvoice, setEinvoice] = useState<SalesOrder | null>(null);
  const { tableProps } = useServerTable('sales-orders', salesOrderApi.list, { companyId, keyword, status });

  const refresh = (id: number) => {
    void queryClient.invalidateQueries({ queryKey: ['sales-order', id] });
    void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
  };

  const save = async (values: OrderForm, key: string) => {
    const input = {
      branchId: values.branchId,
      customerId: values.customerId,
      warehouseId: values.warehouseId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: dateValue(values.postingDate),
      contactName: values.contactName,
      deliveryAddress: values.deliveryAddress,
      receiverName: values.receiverName,
      receiverPhone: values.receiverPhone,
      expectedDate: dateValue(values.expectedDate),
      paymentTermDays: values.paymentTermDays ?? null,
      reserveStock: !!values.reserveStock,
      description: values.description,
      lines: (values.lines ?? []).map(toLineInput),
      paymentTerms: values.paymentTerms ?? [],
    };
    if (editing === 'new') {
      const created = await salesOrderApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await salesOrderApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu đơn bán.');
      void queryClient.invalidateQueries({ queryKey: ['sales-order', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
  };

  const initialValues: Partial<OrderForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          customerId: editing.customerId,
          customerLabel: `${editing.customerCode ?? ''} - ${editing.customerName ?? ''}`,
          warehouseId: editing.warehouseId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          contactName: editing.contactName ?? undefined,
          deliveryAddress: editing.deliveryAddress ?? undefined,
          receiverName: editing.receiverName ?? undefined,
          receiverPhone: editing.receiverPhone ?? undefined,
          expectedDate: editing.expectedDate ? dayjs(editing.expectedDate) : undefined,
          paymentTermDays: editing.paymentTermDays ?? undefined,
          reserveStock: editing.reserveStock,
          description: editing.description ?? undefined,
          lines: editing.lines.map(toLineForm),
          paymentTerms: editing.paymentSchedule.map((term) => ({
            percent: term.percent,
            dueBasis: term.dueBasis,
            dueDays: term.dueDays,
            description: term.description ?? undefined,
          })),
        }
      : {
          branchId: branches.items[0]?.id,
          documentDate: dayjs(),
          lines: [{ quantity: 1 }],
          paymentTerms: [],
        };

  const columns: TableColumnsType<SalesOrder> = [
    {
      title: 'Số đơn',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    { title: 'Khách hàng', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
    { title: 'Nhân viên', dataIndex: 'salesUserName', width: 170 },
    {
      title: 'Hóa đơn',
      width: 150,
      render: (_, row) => (row.einvoiceNo ? `${row.einvoiceSeries}-${row.einvoiceNo}` : ''),
    },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 140, align: 'right', render: formatMoney },
    {
      title: 'Giao hàng',
      dataIndex: 'deliveryStatus',
      width: 110,
      render: (value: DeliveryStatus, row) =>
        row.status === 'POSTED' ? (
          <Tag color={DELIVERY_STATUSES[value].color}>{DELIVERY_STATUSES[value].label}</Tag>
        ) : null,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: SalesDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Đơn bán hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số đơn, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 170 }}
            value={status}
            onChange={setStatus}
            options={POSTING_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập đơn bán"
        onCreate={can('sales-order:create') ? () => setEditing('new') : undefined}
      />
      <Table<SalesOrder> {...tableProps} size="small" columns={columns} scroll={{ x: 1200 }} />
      {selected !== null && (
        <SalesDocumentDrawer<SalesOrder>
          id={selected}
          queryKey="sales-order"
          listKeys={['sales-orders', 'stock-summaries', 'inventory-documents', 'debt-items']}
          load={salesOrderApi.get}
          act={salesOrderApi.act}
          permission="sales-order"
          actions={(document) => orderActions(document, can)}
          actionOrder={ORDER_ACTION_ORDER}
          title={(document) => `Đơn bán ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
          extra={(document) => (
            <>
              {document.status === 'POSTED' && (
                <PrintButton
                  label="In phiếu xuất kho"
                  title={`Phiếu xuất kho ${document.inventoryDocumentNo ?? ''}`}
                >
                  <StockOutSlipPrint orderId={document.id} />
                </PrintButton>
              )}
              {document.status === 'POSTED' && (
                <PrintButton
                  label="In biên bản giao nhận"
                  title={`Biên bản giao nhận ${document.documentNo}`}
                >
                  <DeliveryNote order={document} unitName={units.nameOf} />
                </PrintButton>
              )}
              {document.status === 'POSTED' &&
                document.deliveryStatus !== 'DELIVERED' &&
                can('sales-order:deliver') && (
                  <Button onClick={() => setConfirming(document)}>Khách nhận hàng</Button>
                )}
              {(document.status === 'APPROVED' || document.status === 'POSTED') &&
                can('sales-order:post') && (
                  <Button onClick={() => setEinvoice(document)}>Số hóa đơn điện tử</Button>
                )}
            </>
          )}
        >
          {(document) => (
            <>
              {document.approvalReasons && document.status !== 'POSTED' && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Lý do phải duyệt"
                  description={document.approvalReasons}
                />
              )}
              {document.availabilityWarnings.length > 0 && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Tồn khả dụng không đủ"
                  description={document.availabilityWarnings.join(' ')}
                />
              )}
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Khách hàng">
                  {`${document.customerCode ?? ''} - ${document.customerName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Kho xuất">{`${document.warehouseCode ?? ''} - ${
                  document.warehouseName ?? ''
                }`}</Descriptions.Item>
                <Descriptions.Item label="Nhân viên">{document.salesUserName}</Descriptions.Item>
                <Descriptions.Item label="Ngày đơn">{formatDate(document.documentDate)}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Hạn thanh toán">
                  {document.dueDate
                    ? formatDate(document.dueDate)
                    : document.paymentTermDays !== null
                      ? `${document.paymentTermDays} ngày sau ghi sổ`
                      : ''}
                </Descriptions.Item>
                <Descriptions.Item label="Địa chỉ giao">{document.deliveryAddress}</Descriptions.Item>
                <Descriptions.Item label="Người nhận">
                  {[document.receiverName, document.receiverPhone].filter(Boolean).join(' - ')}
                </Descriptions.Item>
                <Descriptions.Item label="Giữ hàng">
                  {document.reservedUntil
                    ? `Đến ${dayjs(document.reservedUntil).format('DD/MM/YYYY HH:mm')}`
                    : document.reserveStock
                      ? 'Giữ khi duyệt'
                      : 'Không'}
                </Descriptions.Item>
                <Descriptions.Item label="Hóa đơn điện tử">
                  {document.einvoiceNo
                    ? `${document.einvoiceSeries}-${document.einvoiceNo} ngày ${formatDate(document.einvoiceDate)}`
                    : 'Chưa ghi số'}
                </Descriptions.Item>
                <Descriptions.Item label="Phiếu xuất kho">{document.inventoryDocumentNo}</Descriptions.Item>
                <Descriptions.Item label="Giao hàng">
                  {document.status === 'POSTED'
                    ? [
                        DELIVERY_STATUSES[document.deliveryStatus].label,
                        document.shipperName,
                        document.receivedDate ? `nhận ${formatDate(document.receivedDate)}` : null,
                        document.receivedBy,
                        document.signedDocumentRef,
                      ]
                        .filter(Boolean)
                        .join(' · ')
                    : ''}
                </Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng cộng">
                  <strong>{formatMoney(document.totalAmount)}</strong>
                </Descriptions.Item>
                {document.reverseDate && (
                  <Descriptions.Item label="Ngày đảo">{formatDate(document.reverseDate)}</Descriptions.Item>
                )}
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
              </Descriptions>
              <SalesLinesTable
                lines={document.lines}
                posted={document.status === 'POSTED' || document.status === 'REVERSED'}
              />
              {document.paymentSchedule.length > 0 && (
                <>
                  <Typography.Title level={5} style={{ marginTop: 16 }}>
                    Lịch thu tiền
                  </Typography.Title>
                  <Table<PaymentSchedule>
                    rowKey="installmentNo"
                    size="small"
                    pagination={false}
                    columns={scheduleColumns}
                    dataSource={document.paymentSchedule}
                  />
                </>
              )}
              <SourceJournal sourceType="DBH" sourceId={document.id} status={document.status} />
            </>
          )}
        </SalesDocumentDrawer>
      )}
      <FormModal<OrderForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập đơn bán' : 'Sửa đơn bán'}
        width={1200}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <OrderFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
      {confirming && (
        <ConfirmDeliveryModal
          order={confirming}
          onClose={() => setConfirming(null)}
          onDone={() => {
            message.success('Đã ghi nhận khách nhận hàng.');
            refresh(confirming.id);
            setConfirming(null);
          }}
        />
      )}
      {einvoice && (
        <EinvoiceModal
          order={einvoice}
          onClose={() => setEinvoice(null)}
          onDone={() => {
            message.success('Đã ghi số hóa đơn điện tử.');
            refresh(einvoice.id);
            setEinvoice(null);
          }}
        />
      )}
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
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={5}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </Col>
        <Col xs={24} md={7}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách' }]}>
            <PartnerSelect
              role="CUSTOMER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('customerLabel')}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={5}>
          <Form.Item name="warehouseId" label="Kho xuất" rules={[{ required: true, message: 'Chọn kho' }]}>
            <Select
              options={warehouses.items
                .filter((warehouse) => warehouse.branchId === branchId)
                .map((warehouse) => ({
                  value: warehouse.id,
                  label: `${warehouse.code} - ${warehouse.name}`,
                }))}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="documentDate" label="Ngày đơn" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name="postingDate"
            label="Ngày hạch toán"
            tooltip="Ngày xuất kho, ghi doanh thu; trống: ngày đơn"
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item name="reserveStock" valuePropName="checked" label="Giữ hàng khi duyệt">
            <Checkbox />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="expectedDate" label="Giao dự kiến">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="paymentTermDays" label="Số ngày nợ" tooltip="Trống: theo khách">
            <InputNumber min={0} max={3650} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="deliveryAddress" label="Địa chỉ giao">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="receiverName" label="Người nhận">
            <Input maxLength={200} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="receiverPhone" label="Điện thoại">
            <Input maxLength={50} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="contactName" label="Người liên hệ">
            <Input maxLength={200} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <SalesLinesEditor form={form} withExpectedDate />
      <Typography.Title level={5} style={{ marginTop: 16 }}>
        Lịch thu tiền (tùy chọn, tổng 100%)
      </Typography.Title>
      <Form.List name="paymentTerms">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={8} key={field.key}>
                <Col xs={6} md={3}>
                  <Form.Item name={[field.name, 'percent']} rules={[{ required: true, message: 'Nhập %' }]}>
                    <InputNumber min={0.01} max={100} placeholder="%" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={10} md={6}>
                  <Form.Item
                    name={[field.name, 'dueBasis']}
                    rules={[{ required: true, message: 'Chọn mốc' }]}
                  >
                    <Select
                      placeholder="Mốc"
                      options={(Object.keys(DUE_BASES) as PaymentDueBasis[]).map((value) => ({
                        value,
                        label: DUE_BASES[value],
                      }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={6} md={3}>
                  <Form.Item name={[field.name, 'dueDays']} rules={[{ required: true, message: 'Số ngày' }]}>
                    <InputNumber min={0} max={3650} placeholder="Ngày" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={20} md={11}>
                  <Form.Item name={[field.name, 'description']}>
                    <Input placeholder="Điều kiện" maxLength={500} />
                  </Form.Item>
                </Col>
                <Col xs={4} md={1}>
                  <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                </Col>
              </Row>
            ))}
            <Button icon={<PlusOutlined />} onClick={() => add({ dueBasis: 'ORDER_DATE', dueDays: 0 })}>
              Thêm đợt thu tiền
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
