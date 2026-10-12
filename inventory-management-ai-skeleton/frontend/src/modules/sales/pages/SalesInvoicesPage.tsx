import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
  InputNumber,
  Modal,
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
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import { SourceJournal } from '../../purchasing/components/SourceJournal';
import { useMyBranches } from '../../system/lookups';
import { salesInvoiceApi } from '../api';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { DELIVERY_STATUSES, POSTING_STATUSES, postingActions } from '../labels';
import type { InvoiceableSalesLine, SalesDocumentStatus, SalesInvoice, SalesInvoiceLine } from '../types';

interface LineForm {
  sourceKey?: string;
  quantity?: number;
  description?: string;
}

interface InvoiceForm {
  branchId: number;
  customerId?: number;
  customerLabel?: string;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  invoiceDate?: Dayjs;
  dueDate?: Dayjs;
  buyerName?: string;
  buyerTaxCode?: string;
  buyerAddress?: string;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'branchId',
  'customerId',
  'documentDate',
  'postingDate',
  'invoiceDate',
  'dueDate',
  'buyerName',
  'buyerTaxCode',
  'buyerAddress',
  'description',
  'lines',
];

const dateValue = (value?: Dayjs) => (value ? value.format('YYYY-MM-DD') : null);

/** Khóa dòng nguồn: dòng phiếu giao (D) hoặc dòng dịch vụ của đơn (O). */
const sourceKey = (line: { deliveryLineId: number | null; orderLineId: number }) =>
  line.deliveryLineId ? `D${line.deliveryLineId}` : `O${line.orderLineId}`;

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<SalesInvoiceLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    {
      title: 'Hàng hóa / dịch vụ',
      render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}`,
    },
    { title: 'Phiếu giao', dataIndex: 'deliveryNo', width: 170 },
    { title: 'Đơn bán', dataIndex: 'orderNo', width: 170 },
    {
      title: 'Số lượng',
      width: 110,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 110, align: 'right', render: formatMoney },
    {
      title: 'CK %',
      dataIndex: 'discountPercent',
      width: 60,
      align: 'right',
      render: (value: number) => formatNumber(value, 2),
    },
    { title: 'Thành tiền', dataIndex: 'amount', width: 120, align: 'right', render: formatMoney },
    { title: 'Tiền thuế', dataIndex: 'taxAmount', width: 100, align: 'right', render: formatMoney },
    { title: 'Đã giảm', dataIndex: 'adjustedAmount', width: 100, align: 'right', render: formatMoney },
  ];
}

/**
 * Hóa đơn bán (BR-SAL-30, 31, D-35): từ dòng phiếu giao đã xuất kho (nhiều phiếu) và dòng dịch vụ của đơn bán; cảnh
 * báo lập trước khi khách nhận hàng; số hóa đơn điện tử phát hành trên MISA; ghi sổ công nợ phải thu, doanh thu; đảo.
 */
export function SalesInvoicesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SalesDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<SalesInvoice | 'new' | null>(null);
  const [einvoice, setEinvoice] = useState<SalesInvoice | null>(null);
  const { tableProps } = useServerTable('sales-invoices', salesInvoiceApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: InvoiceForm, key: string) => {
    const input = {
      branchId: values.branchId,
      customerId: values.customerId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: dateValue(values.postingDate),
      invoiceDate: dateValue(values.invoiceDate),
      dueDate: dateValue(values.dueDate),
      buyerName: values.buyerName,
      buyerTaxCode: values.buyerTaxCode,
      buyerAddress: values.buyerAddress,
      description: values.description,
      lines: (values.lines ?? []).map((line) => {
        const id = Number(line.sourceKey?.slice(1));
        const delivery = line.sourceKey?.startsWith('D');
        return {
          deliveryLineId: delivery ? id : null,
          orderLineId: delivery ? null : id,
          quantity: line.quantity,
          description: line.description,
        };
      }),
    };
    if (editing === 'new') {
      const created = await salesInvoiceApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await salesInvoiceApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu hóa đơn.');
      void queryClient.invalidateQueries({ queryKey: ['sales-invoice', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['sales-invoices'] });
  };

  const initialValues: Partial<InvoiceForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          customerId: editing.customerId,
          customerLabel: `${editing.customerCode ?? ''} - ${editing.customerName ?? ''}`,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          invoiceDate: dayjs(editing.invoiceDate),
          dueDate: dayjs(editing.dueDate),
          buyerName: editing.buyerName ?? undefined,
          buyerTaxCode: editing.buyerTaxCode ?? undefined,
          buyerAddress: editing.buyerAddress ?? undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            sourceKey: sourceKey(line),
            quantity: line.quantity,
            description: line.description ?? undefined,
          })),
        }
      : { branchId: branches.items[0]?.id, documentDate: dayjs(), lines: [] };

  const columns: TableColumnsType<SalesInvoice> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    {
      title: 'Số HĐĐT',
      width: 160,
      render: (_, row) => (row.einvoiceNo ? `${row.einvoiceSeries}-${row.einvoiceNo}` : ''),
    },
    { title: 'Ngày HĐ', dataIndex: 'invoiceDate', width: 110, render: formatDate },
    { title: 'Khách hàng', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
    { title: 'Hạn thanh toán', dataIndex: 'dueDate', width: 120, render: formatDate },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 150, align: 'right', render: formatMoney },
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
        Hóa đơn bán
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
            options={POSTING_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập hóa đơn"
        onCreate={can('sales-invoice:create') ? () => setEditing('new') : undefined}
      />
      <Table<SalesInvoice> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <SalesDocumentDrawer<SalesInvoice>
          id={selected}
          queryKey="sales-invoice"
          listKeys={['sales-invoices', 'debt-items', 'sales-order']}
          load={salesInvoiceApi.get}
          act={salesInvoiceApi.act}
          permission="sales-invoice"
          actions={(document) => postingActions(document.status, 'sales-invoice', can)}
          title={(document) => `Hóa đơn bán ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
          extra={(document) =>
            document.status !== 'CANCELLED' &&
            document.status !== 'REVERSED' &&
            can('sales-invoice:create') && (
              <Button onClick={() => setEinvoice(document)}>Số HĐĐT (MISA)</Button>
            )
          }
        >
          {(document) => (
            <>
              {document.warnings.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Hóa đơn lập trước khi khách nhận hàng"
                  description={document.warnings.join(' ')}
                />
              )}
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Khách hàng">
                  {`${document.customerCode ?? ''} - ${document.customerName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Người mua">{document.buyerName}</Descriptions.Item>
                <Descriptions.Item label="MST người mua">{document.buyerTaxCode}</Descriptions.Item>
                <Descriptions.Item label="Ngày hóa đơn">{formatDate(document.invoiceDate)}</Descriptions.Item>
                <Descriptions.Item label="Hạn thanh toán">{formatDate(document.dueDate)}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="HĐĐT (MISA)">
                  {document.einvoiceNo
                    ? `${document.einvoiceSeries}-${document.einvoiceNo} ngày ${formatDate(document.einvoiceDate)}${
                        document.taxAuthorityCode ? ` · mã CQT ${document.taxAuthorityCode}` : ''
                      }`
                    : 'Chưa có'}
                </Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng thanh toán">
                  <strong>{formatMoney(document.totalAmount)}</strong>
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
              </Descriptions>
              <Table<SalesInvoiceLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1300 }}
              />
              <SourceJournal sourceType="HDB" sourceId={document.id} status={document.status} />
            </>
          )}
        </SalesDocumentDrawer>
      )}
      <FormModal<InvoiceForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập hóa đơn bán' : 'Sửa hóa đơn bán'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <InvoiceFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
      {einvoice && (
        <EinvoiceModal
          invoice={einvoice}
          onClose={() => setEinvoice(null)}
          onDone={() => {
            message.success('Đã ghi số hóa đơn điện tử.');
            void queryClient.invalidateQueries({ queryKey: ['sales-invoice', einvoice.id] });
            void queryClient.invalidateQueries({ queryKey: ['sales-invoices'] });
            setEinvoice(null);
          }}
        />
      )}
    </Card>
  );
}

function InvoiceFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<InvoiceForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const units = useCatalog('units');
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const customerId = Form.useWatch('customerId', form) as number | undefined;
  const invoiceable = useQuery({
    queryKey: ['sales-invoices', 'invoiceable', branchId, customerId],
    queryFn: () =>
      salesInvoiceApi.invoiceableLines({ branchId: branchId as number, customerId: customerId as number }),
    enabled: typeof branchId === 'number' && typeof customerId === 'number',
  });
  const sources = invoiceable.data ?? [];
  const label = (line: InvoiceableSalesLine) =>
    `${line.deliveryNo ?? `Dịch vụ · ${line.orderNo}`} · ${line.productCode ?? ''} - ${line.productName ?? ''} (còn ${formatNumber(
      line.remainingQuantity,
    )} ${units.nameOf(line.unitId)})${
      line.deliveryStatus && line.deliveryStatus !== 'DELIVERED'
        ? ` · ${DELIVERY_STATUSES[line.deliveryStatus].label}`
        : ''
    }`;
  const addAll = () => {
    const current = (form.getFieldValue('lines') as LineForm[] | undefined) ?? [];
    const used = new Set(current.map((line) => line.sourceKey));
    form.setFieldValue('lines', [
      ...current,
      ...sources
        .filter((line) => !used.has(sourceKey(line)))
        .map((line) => ({ sourceKey: sourceKey(line), quantity: line.remainingQuantity })),
    ]);
  };
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
              onChange={() => form.setFieldValue('lines', [])}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách' }]}>
            <PartnerSelect
              role="CUSTOMER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('customerLabel')}
              onChange={() => form.setFieldValue('lines', [])}
            />
          </Form.Item>
        </Col>
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
          <Form.Item name="invoiceDate" label="Ngày hóa đơn" extra="Trống: ngày chứng từ">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={5}>
          <Form.Item name="dueDate" label="Hạn thanh toán" extra="Trống: theo đơn / khách">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={5}>
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={7}>
          <Form.Item name="buyerName" label="Người mua" extra="Trống: theo khách">
            <Input maxLength={255} />
          </Form.Item>
        </Col>
        <Col xs={24} md={7}>
          <Form.Item name="buyerTaxCode" label="MST người mua">
            <Input maxLength={20} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="buyerAddress" label="Địa chỉ người mua">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={8} key={field.key}>
                <Col xs={24} md={14}>
                  <Form.Item
                    name={[field.name, 'sourceKey']}
                    rules={[{ required: true, message: 'Chọn dòng phiếu giao / dịch vụ' }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="Dòng phiếu giao / dịch vụ"
                      options={sources.map((line) => ({ value: sourceKey(line), label: label(line) }))}
                      onChange={(value: string) => {
                        const picked = sources.find((line) => sourceKey(line) === value);
                        if (picked) {
                          form.setFieldValue(['lines', field.name, 'quantity'], picked.remainingQuantity);
                        }
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col xs={10} md={3}>
                  <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: 'Nhập SL' }]}>
                    <InputNumber min={0.001} precision={3} placeholder="Số lượng" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                  <Form.Item name={[field.name, 'description']}>
                    <Input placeholder="Ghi chú" maxLength={500} />
                  </Form.Item>
                </Col>
                <Col xs={2} md={1}>
                  <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                </Col>
              </Row>
            ))}
            <Space wrap>
              <Button
                icon={<PlusOutlined />}
                disabled={sources.length === 0}
                loading={invoiceable.isLoading}
                onClick={addAll}
              >
                Thêm mọi dòng chưa xuất hóa đơn
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => add({})}>
                Thêm dòng
              </Button>
            </Space>
          </>
        )}
      </Form.List>
    </>
  );
}

/** Ghi ký hiệu, số, ngày, mã cơ quan thuế của hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31). */
function EinvoiceModal({
  invoice,
  onClose,
  onDone,
}: {
  invoice: SalesInvoice;
  onClose(): void;
  onDone(): void;
}) {
  const [form] = Form.useForm<{
    series?: string;
    number?: string;
    date?: Dayjs;
    taxAuthorityCode?: string;
  }>();
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
      await salesInvoiceApi.act(
        invoice.id,
        'einvoice-reference',
        { ...values, date: values.date ? values.date.format('YYYY-MM-DD') : null },
        key,
      );
      onDone();
    } catch (err) {
      setError(applyServerErrors(form, err, ['series', 'number', 'date', 'taxAuthorityCode']));
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <Modal
      open
      title={`Số hóa đơn điện tử - ${invoice.documentNo}`}
      okText="Lưu"
      okButtonProps={{ loading: submitting }}
      onOk={submit}
      onCancel={onClose}
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          series: invoice.einvoiceSeries ?? undefined,
          number: invoice.einvoiceNo ?? undefined,
          date: invoice.einvoiceDate ? dayjs(invoice.einvoiceDate) : dayjs(invoice.invoiceDate),
          taxAuthorityCode: invoice.taxAuthorityCode ?? undefined,
        }}
      >
        <Form.Item
          name="series"
          label="Ký hiệu"
          rules={[{ required: true, whitespace: true, message: 'Nhập ký hiệu' }]}
        >
          <Input maxLength={20} />
        </Form.Item>
        <Form.Item
          name="number"
          label="Số hóa đơn"
          rules={[{ required: true, whitespace: true, message: 'Nhập số' }]}
        >
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="date" label="Ngày phát hành" rules={[{ required: true, message: 'Chọn ngày' }]}>
          <DatePicker format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item name="taxAuthorityCode" label="Mã cơ quan thuế">
          <Input maxLength={50} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
