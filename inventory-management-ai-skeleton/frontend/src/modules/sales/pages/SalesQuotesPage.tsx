import { useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Table,
  Typography,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useIdempotencyKey } from '../../../shared/api/useIdempotencyKey';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { applyServerErrors, validated } from '../../../shared/forms';
import { formatMoney } from '../../accounting/labels';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { salesQuoteApi } from '../api';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { SalesLinesEditor } from '../components/SalesLineFields';
import { toLineForm, toLineInput, type SalesLineForm } from '../components/salesLines';
import { PrintButton, SalesLinesTable } from '../components/SalesLinesTable';
import { QUOTE_ACTION_ORDER, QUOTE_STATUSES, quoteActions } from '../labels';
import type { SalesDocumentStatus, SalesQuote } from '../types';

interface QuoteForm {
  branchId: number;
  customerId?: number;
  customerLabel?: string;
  documentDate: Dayjs;
  contactName?: string;
  validUntil?: Dayjs;
  deliveryTerms?: string;
  warrantyTerms?: string;
  paymentTerms?: string;
  description?: string;
  lines: SalesLineForm[];
}

const FIELDS = [
  'branchId',
  'customerId',
  'documentDate',
  'contactName',
  'validUntil',
  'deliveryTerms',
  'warrantyTerms',
  'paymentTerms',
  'description',
  'lines',
];

/**
 * Báo giá (BR-SAL-01..05): giá theo bảng giá ưu tiên, duyệt giá dưới sàn / chiết khấu vượt ngưỡng, gửi khách, khách
 * chấp nhận / từ chối, phiên bản mới, chuyển thành đơn bán, in báo giá.
 */
export function SalesQuotesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SalesDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<SalesQuote | 'new' | null>(null);
  const [converting, setConverting] = useState<SalesQuote | null>(null);
  const { tableProps } = useServerTable('sales-quotes', salesQuoteApi.list, { companyId, keyword, status });

  const save = async (values: QuoteForm, key: string) => {
    const input = {
      branchId: values.branchId,
      customerId: values.customerId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      contactName: values.contactName,
      validUntil: values.validUntil ? values.validUntil.format('YYYY-MM-DD') : null,
      deliveryTerms: values.deliveryTerms,
      warrantyTerms: values.warrantyTerms,
      paymentTerms: values.paymentTerms,
      description: values.description,
      lines: (values.lines ?? []).map(toLineInput),
    };
    if (editing === 'new') {
      const created = await salesQuoteApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await salesQuoteApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu báo giá.');
      void queryClient.invalidateQueries({ queryKey: ['sales-quote', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['sales-quotes'] });
  };

  const initialValues: Partial<QuoteForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          customerId: editing.customerId,
          customerLabel: `${editing.customerCode ?? ''} - ${editing.customerName ?? ''}`,
          documentDate: dayjs(editing.documentDate),
          contactName: editing.contactName ?? undefined,
          validUntil: dayjs(editing.validUntil),
          deliveryTerms: editing.deliveryTerms ?? undefined,
          warrantyTerms: editing.warrantyTerms ?? undefined,
          paymentTerms: editing.paymentTerms ?? undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map(toLineForm),
        }
      : { branchId: branches.items[0]?.id, documentDate: dayjs(), lines: [{ quantity: 1 }] };

  const columns: TableColumnsType<SalesQuote> = [
    {
      title: 'Số báo giá',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'PB', dataIndex: 'revisionNo', width: 50 },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    { title: 'Khách hàng', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
    { title: 'Hiệu lực đến', dataIndex: 'validUntil', width: 120, render: formatDate },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 150, align: 'right', render: formatMoney },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: SalesDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Báo giá
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số báo giá, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 170 }}
            value={status}
            onChange={setStatus}
            options={QUOTE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập báo giá"
        onCreate={can('sales-quote:create') ? () => setEditing('new') : undefined}
      />
      <Table<SalesQuote> {...tableProps} size="small" columns={columns} scroll={{ x: 1000 }} />
      {selected !== null && (
        <SalesDocumentDrawer<SalesQuote>
          id={selected}
          queryKey="sales-quote"
          listKeys={['sales-quotes']}
          load={salesQuoteApi.get}
          act={salesQuoteApi.act}
          permission="sales-quote"
          actions={(document) => quoteActions(document.status, can)}
          actionOrder={QUOTE_ACTION_ORDER}
          title={(document) => `Báo giá ${document.documentNo} (phiên bản ${document.revisionNo})`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
          extra={(document) => (
            <>
              <PrintButton label="In báo giá" title={`Báo giá ${document.documentNo}`}>
                <QuotePrint quote={document} />
              </PrintButton>
              {document.status === 'ACCEPTED' && !document.salesOrderId && can('sales-order:create') && (
                <Button type="primary" onClick={() => setConverting(document)}>
                  Chuyển thành đơn bán
                </Button>
              )}
            </>
          )}
        >
          {(document) => (
            <>
              {document.approvalReasons && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Phải duyệt giá"
                  description={document.approvalReasons}
                />
              )}
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Khách hàng">
                  {`${document.customerCode ?? ''} - ${document.customerName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Người liên hệ">{document.contactName}</Descriptions.Item>
                <Descriptions.Item label="Hiệu lực đến">{formatDate(document.validUntil)}</Descriptions.Item>
                <Descriptions.Item label="Nhân viên">{document.salesUserName}</Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Chiết khấu">
                  {formatMoney(document.discountAmount)}
                </Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng cộng">
                  <strong>{formatMoney(document.totalAmount)}</strong>
                </Descriptions.Item>
                <Descriptions.Item label="Đơn bán">
                  {document.salesOrderId ? `#${document.salesOrderId}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="Điều kiện giao">{document.deliveryTerms}</Descriptions.Item>
                <Descriptions.Item label="Bảo hành">{document.warrantyTerms}</Descriptions.Item>
                <Descriptions.Item label="Thanh toán">{document.paymentTerms}</Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
              </Descriptions>
              <SalesLinesTable lines={document.lines} />
            </>
          )}
        </SalesDocumentDrawer>
      )}
      <FormModal<QuoteForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập báo giá' : 'Sửa báo giá'}
        width={1200}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <QuoteFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
      {converting && (
        <ConvertModal
          quote={converting}
          onClose={() => setConverting(null)}
          onDone={(orderNo) => {
            message.success(`Đã lập đơn bán ${orderNo}.`);
            setConverting(null);
            void queryClient.invalidateQueries({ queryKey: ['sales-quote', converting.id] });
            void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
          }}
        />
      )}
    </Card>
  );
}

function QuoteFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<QuoteForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
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
        <Col xs={24} md={8}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách' }]}>
            <PartnerSelect
              role="CUSTOMER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('customerLabel')}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name="documentDate"
            label="Ngày báo giá"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="validUntil" label="Hiệu lực đến" extra="Trống: theo tham số">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={3}>
          <Form.Item name="contactName" label="Người liên hệ">
            <Input maxLength={200} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="deliveryTerms" label="Điều kiện giao hàng">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="warrantyTerms" label="Bảo hành">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="paymentTerms" label="Điều kiện thanh toán">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <SalesLinesEditor form={form} />
    </>
  );
}

/** Chuyển báo giá khách đã chấp nhận thành đơn bán: chọn kho xuất, ngày giao, giữ hàng. */
function ConvertModal({
  quote,
  onClose,
  onDone,
}: {
  quote: SalesQuote;
  onClose(): void;
  onDone(orderNo: string): void;
}) {
  const [form] = Form.useForm<{ warehouseId?: number; expectedDate?: Dayjs; deliveryAddress?: string }>();
  const warehouses = useWarehouses();
  const { key } = useIdempotencyKey();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submit = async () => {
    const values = await validated(form);
    if (!values) {
      return;
    }
    setSubmitting(true);
    try {
      const order = await salesQuoteApi.createOrder(
        quote.id,
        {
          warehouseId: values.warehouseId,
          expectedDate: values.expectedDate ? values.expectedDate.format('YYYY-MM-DD') : null,
          deliveryAddress: values.deliveryAddress,
        },
        key,
      );
      onDone(order.documentNo);
    } catch (err) {
      setError(applyServerErrors(form, err, ['warehouseId', 'expectedDate', 'deliveryAddress']));
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <Modal
      open
      title={`Chuyển báo giá ${quote.documentNo} thành đơn bán`}
      okText="Lập đơn bán"
      okButtonProps={{ loading: submitting }}
      onOk={submit}
      onCancel={onClose}
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Form form={form} layout="vertical">
        <Form.Item name="warehouseId" label="Kho xuất" rules={[{ required: true, message: 'Chọn kho' }]}>
          <Select
            options={warehouses.items
              .filter((warehouse) => warehouse.branchId === quote.branchId)
              .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} - ${warehouse.name}` }))}
          />
        </Form.Item>
        <Form.Item name="expectedDate" label="Ngày giao dự kiến">
          <DatePicker format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item name="deliveryAddress" label="Địa chỉ giao">
          <Input maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** Bản in báo giá (BR-SAL-04): khách, hiệu lực, điều kiện, dòng hàng, tổng tiền. */
function QuotePrint({ quote }: { quote: SalesQuote }) {
  return (
    <div>
      <Typography.Title level={4} style={{ textAlign: 'center' }}>
        BÁO GIÁ {quote.documentNo}
      </Typography.Title>
      <p>
        Kính gửi: <strong>{quote.customerName}</strong>
        {quote.contactName ? ` - ${quote.contactName}` : ''}
      </p>
      <p>
        Ngày: {formatDate(quote.documentDate)} · Hiệu lực đến: {formatDate(quote.validUntil)} · Phiên bản{' '}
        {quote.revisionNo}
      </p>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {['#', 'Hàng hóa', 'Số lượng', 'Đơn giá', 'CK %', 'Thành tiền', 'Thuế'].map((head) => (
              <th key={head} style={{ border: '1px solid #999', padding: 4 }}>
                {head}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {quote.lines.map((line) => (
            <tr key={line.id}>
              <td style={{ border: '1px solid #999', padding: 4 }}>{line.lineNo}</td>
              <td style={{ border: '1px solid #999', padding: 4 }}>
                {line.productCode} - {line.productName}
              </td>
              <td style={{ border: '1px solid #999', padding: 4, textAlign: 'right' }}>
                {formatNumber(line.quantity)}
              </td>
              <td style={{ border: '1px solid #999', padding: 4, textAlign: 'right' }}>
                {formatMoney(line.unitPrice)}
              </td>
              <td style={{ border: '1px solid #999', padding: 4, textAlign: 'right' }}>
                {formatNumber(line.discountPercent, 2)}
              </td>
              <td style={{ border: '1px solid #999', padding: 4, textAlign: 'right' }}>
                {formatMoney(line.amount)}
              </td>
              <td style={{ border: '1px solid #999', padding: 4, textAlign: 'right' }}>
                {formatMoney(line.taxAmount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p style={{ textAlign: 'right', marginTop: 8 }}>
        Tiền hàng: {formatMoney(quote.amount)} · Thuế GTGT: {formatMoney(quote.taxAmount)} ·{' '}
        <strong>Tổng cộng: {formatMoney(quote.totalAmount)}</strong>
      </p>
      <p>Điều kiện giao hàng: {quote.deliveryTerms}</p>
      <p>Bảo hành: {quote.warrantyTerms}</p>
      <p>Điều kiện thanh toán: {quote.paymentTerms}</p>
    </div>
  );
}
