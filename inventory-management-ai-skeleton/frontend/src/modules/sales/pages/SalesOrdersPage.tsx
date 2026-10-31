import { CopyOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
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
  Space,
  Table,
  Tooltip,
  Tag,
  Typography,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
import { StockOutSlipDownload, StockOutSlipPrint } from '../components/StockOutSlipPrint';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { SalesLinesEditor } from '../components/SalesLineFields';
import { toLineForm, toLineInput, type SalesLineForm } from '../components/salesLines';
import { PrintButton } from '../../../shared/components/PrintButton';
import { CompanyPrintButton } from '../../../shared/components/CompanyPrintButton';
import { SalesLinesTable } from '../components/SalesLinesTable';
import {
  DELIVERY_STATUSES,
  DUE_BASES,
  ORDER_ACTION_ORDER,
  ORDER_ROW_ACTION_ORDER,
  ORDER_STATUSES,
  orderActions,
} from '../labels';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import type {
  DeliveryStatus,
  PaymentDueBasis,
  PaymentSchedule,
  SalesDocumentStatus,
  SalesOrder,
} from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';
import { SalesLineImportButton } from '../components/SalesLineImportButton';
import { SalesOcrButton } from '../components/SalesOcrButton';
import { OcrNotes } from '../../ai/components/OcrHints';

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

const LIST_KEYS = ['sales-orders', 'stock-summaries', 'inventory-documents', 'debt-items'];

/**
 * Đơn bán (BR-SAL-10..14, 21..23, 30, 31, 50, D-54): chứng từ duy nhất của nghiệp vụ bán, không qua duyệt (D-61). Giá
 * theo bảng giá, thẩm quyền giá / hạn mức tín dụng khi ghi sổ, tồn khả dụng, giữ / nhả hàng; ghi sổ = xuất kho (FEFO / lô chỉ định) + doanh thu + công nợ
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
  // Mở thẳng chứng từ từ liên kết ?id= (truy ngược từ sổ chi tiết bán hàng, BR-RPT-11).
  const [searchParams] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(() => Number(searchParams.get('id')) || null);
  const [editing, setEditing] = useState<SalesOrder | 'new' | null>(null);
  // Đơn nguồn khi lập đơn mới bằng sao chép (chỉ điền sẵn form, server kiểm tra lại như lập mới).
  const [copySource, setCopySource] = useState<SalesOrder | null>(null);
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
      ? orderForm(editing)
      : copySource
        ? copyForm(copySource)
        : {
            branchId: branches.defaultId,
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
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_, row) => (
        <Space size={0}>
          {can('sales-order:create') && (
            <Tooltip title="Sao chép">
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined aria-hidden />}
                aria-label="Sao chép"
                onClick={() => void copyOrder(row.id)}
              />
            </Tooltip>
          )}
          <DocumentActions
            iconOnly
            actions={orderActions(row, can)}
            order={ORDER_ROW_ACTION_ORDER}
            onAction={async (action, body, key) => {
              await salesOrderApi.act(row.id, action, body, key);
              void queryClient.invalidateQueries({ queryKey: ['sales-order', row.id] });
              LIST_KEYS.forEach((listKey) => void queryClient.invalidateQueries({ queryKey: [listKey] }));
            }}
          />
        </Space>
      ),
    },
  ];

  /** Sao chép: tải chi tiết đơn (đủ dòng) rồi mở form lập đơn mới điền sẵn. */
  const copyOrder = async (id: number) => {
    try {
      setCopySource(await salesOrderApi.get(id));
      setEditing('new');
    } catch {
      message.error('Không tải được đơn bán để sao chép.');
    }
  };

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
            options={ORDER_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập đơn bán"
        onCreate={
          can('sales-order:create')
            ? () => {
                setCopySource(null);
                setEditing('new');
              }
            : undefined
        }
      />
      <Table<SalesOrder> {...tableProps} size="small" columns={columns} scroll={{ x: 1200 }} />
      {selected !== null && (
        <SalesDocumentDrawer<SalesOrder>
          id={selected}
          queryKey="sales-order"
          listKeys={LIST_KEYS}
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
                <CompanyPrintButton
                  label="In phiếu xuất kho"
                  queryKey="sales-orders"
                  fetchCompanies={salesOrderApi.printCompanies}
                  defaultCompanyId={companyId}
                  extra={(printCompanyId) => (
                    <StockOutSlipDownload orderId={document.id} companyId={printCompanyId} />
                  )}
                >
                  {(printCompanyId) => <StockOutSlipPrint orderId={document.id} companyId={printCompanyId} />}
                </CompanyPrintButton>
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
              {['DRAFT', 'APPROVED', 'POSTED'].includes(document.status) && can('sales-order:post') && (
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
                  message="Cần người có quyền vượt khi ghi sổ"
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
                      ? 'Có, hiện không giữ'
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
        title={
          editing !== 'new'
            ? 'Sửa đơn bán'
            : copySource
              ? `Lập đơn bán (sao chép ${copySource.documentNo})`
              : 'Lập đơn bán'
        }
        width={1200}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => {
          setEditing(null);
          setCopySource(null);
        }}
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

function orderForm(document: SalesOrder): Partial<OrderForm> {
  return {
    branchId: document.branchId,
    customerId: document.customerId,
    customerLabel: `${document.customerCode ?? ''} - ${document.customerName ?? ''}`,
    warehouseId: document.warehouseId,
    documentDate: dayjs(document.documentDate),
    postingDate: dayjs(document.postingDate),
    contactName: document.contactName ?? undefined,
    deliveryAddress: document.deliveryAddress ?? undefined,
    receiverName: document.receiverName ?? undefined,
    receiverPhone: document.receiverPhone ?? undefined,
    expectedDate: document.expectedDate ? dayjs(document.expectedDate) : undefined,
    paymentTermDays: document.paymentTermDays ?? undefined,
    reserveStock: document.reserveStock,
    description: document.description ?? undefined,
    lines: document.lines.map(toLineForm),
    paymentTerms: document.paymentSchedule.map((term) => ({
      percent: term.percent,
      dueBasis: term.dueBasis,
      dueDays: term.dueDays,
      description: term.description ?? undefined,
    })),
  };
}

/**
 * Sao chép đơn bán để nhập nhanh: giữ khách, kho, người nhận, hàng, số lượng, giá, chiết khấu, thuế, lịch thanh toán;
 * ngày đơn là hôm nay. Bỏ ngày hạch toán / giao, giữ hàng, lô / serial chỉ định (chọn lại theo tồn hiện tại, FEFO).
 */
function copyForm(source: SalesOrder): Partial<OrderForm> {
  const form = orderForm(source);
  return {
    ...form,
    documentDate: dayjs(),
    postingDate: undefined,
    expectedDate: undefined,
    reserveStock: false,
    lines: (form.lines ?? []).map((line) => ({
      ...line,
      expectedDate: undefined,
      lotNo: undefined,
      lotOverrideReason: undefined,
      serialNumbers: [],
      receiptTransactionId: undefined,
      receiptNo: undefined,
    })),
  };
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
  const [ocrNotes, setOcrNotes] = useState<string[]>([]);
  return (
    <>
      <Row gutter={12}>
        <BranchCol xs={24} md={5}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </BranchCol>
        <Col xs={24} md={7}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách' }]}>
            <PartnerSelect
              role="CUSTOMER"
              creatable
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
          <Form.Item name="reserveStock" valuePropName="checked" label="Giữ hàng khi lưu">
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
      <OcrNotes notes={ocrNotes} onClose={() => setOcrNotes([])} />
      <SalesLinesEditor
        form={form}
        withExpectedDate
        actions={
          <SalesLineImportButton form={form} extra={<SalesOcrButton form={form} onNotes={setOcrNotes} />} />
        }
      />
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
