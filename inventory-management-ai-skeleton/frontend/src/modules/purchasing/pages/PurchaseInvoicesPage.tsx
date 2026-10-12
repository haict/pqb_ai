import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
import { useCatalog, useCompanyId, useTaxRates } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { purchaseInvoiceApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { SourceJournal } from '../components/SourceJournal';
import { useProductUnits } from '../components/productUnits';
import { invoiceActions, RECEIPT_STATUSES } from '../labels';
import type { InvoiceableLine, PurchaseDocumentStatus, PurchaseInvoice, PurchaseInvoiceLine } from '../types';

type LineKind = 'GOODS' | 'SERVICE';

interface LineForm {
  kind: LineKind;
  receiptLineId?: number;
  productId?: number;
  productLabel?: string;
  unitId?: number;
  quantity?: number;
  unitPrice?: number;
  taxRateId?: number;
  promotional?: boolean;
  description?: string;
}

interface InvoiceForm {
  branchId: number;
  supplierId?: number;
  supplierLabel?: string;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  sellerTaxCode?: string;
  invoiceSeries?: string;
  invoiceNo?: string;
  invoiceDate: Dayjs;
  dueDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'branchId',
  'supplierId',
  'documentDate',
  'postingDate',
  'sellerTaxCode',
  'invoiceSeries',
  'invoiceNo',
  'invoiceDate',
  'dueDate',
  'description',
  'lines',
];

const dateValue = (value?: Dayjs) => (value ? value.format('YYYY-MM-DD') : null);

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseInvoiceLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    {
      title: 'Hàng hóa / dịch vụ',
      render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}`,
    },
    { title: 'Phiếu nhận', dataIndex: 'receiptNo', width: 170 },
    {
      title: 'Số lượng',
      width: 120,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 120, align: 'right', render: formatMoney },
    {
      title: 'Giá đơn mua',
      dataIndex: 'provisionalUnitPrice',
      width: 120,
      align: 'right',
      render: formatMoney,
    },
    { title: 'Thành tiền', dataIndex: 'amount', width: 130, align: 'right', render: formatMoney },
    {
      title: 'Thuế %',
      dataIndex: 'taxRate',
      width: 70,
      align: 'right',
      render: (value: number | null) => formatNumber(value),
    },
    { title: 'Tiền thuế', dataIndex: 'taxAmount', width: 120, align: 'right', render: formatMoney },
    {
      title: 'KM',
      dataIndex: 'promotional',
      width: 50,
      render: (value: boolean) => (value ? 'Có' : ''),
    },
  ];
}

/**
 * Hóa đơn mua (HDM, BR-PUR-09..11, 16, 17): theo dòng phiếu nhận hàng đã nhập kho (nhiều phiếu một hóa đơn) và dịch
 * vụ; chống trùng; cảnh báo đối chiếu ba chiều; ghi sổ sinh công nợ phải trả và bút toán; đảo.
 */
export function PurchaseInvoicesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseInvoice | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-invoices', purchaseInvoiceApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: InvoiceForm, key: string) => {
    const input = {
      branchId: values.branchId,
      supplierId: values.supplierId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: dateValue(values.postingDate),
      sellerTaxCode: values.sellerTaxCode,
      invoiceSeries: values.invoiceSeries,
      invoiceNo: values.invoiceNo,
      invoiceDate: values.invoiceDate.format('YYYY-MM-DD'),
      dueDate: dateValue(values.dueDate),
      description: values.description,
      lines: (values.lines ?? []).map((line) =>
        line.kind === 'GOODS'
          ? {
              receiptLineId: line.receiptLineId,
              quantity: line.quantity,
              unitPrice: line.unitPrice,
              taxRateId: line.taxRateId ?? null,
              promotional: !!line.promotional,
              description: line.description,
            }
          : {
              productId: line.productId,
              unitId: line.unitId,
              quantity: line.quantity,
              unitPrice: line.unitPrice,
              taxRateId: line.taxRateId ?? null,
              promotional: !!line.promotional,
              description: line.description,
            },
      ),
    };
    if (editing === 'new') {
      const created = await purchaseInvoiceApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await purchaseInvoiceApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu hóa đơn.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-invoice', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-invoices'] });
  };

  const initialValues: Partial<InvoiceForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          supplierId: editing.supplierId,
          supplierLabel: `${editing.supplierCode ?? ''} - ${editing.supplierName ?? ''}`,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          sellerTaxCode: editing.sellerTaxCode,
          invoiceSeries: editing.invoiceSeries,
          invoiceNo: editing.invoiceNo,
          invoiceDate: dayjs(editing.invoiceDate),
          dueDate: dayjs(editing.dueDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            kind: line.receiptLineId ? 'GOODS' : 'SERVICE',
            receiptLineId: line.receiptLineId ?? undefined,
            productId: line.productId,
            productLabel: `${line.productCode ?? ''} - ${line.productName ?? ''}`,
            unitId: line.unitId,
            quantity: line.quantity,
            unitPrice: line.unitPrice,
            taxRateId: line.taxRateId ?? undefined,
            promotional: line.promotional,
            description: line.description ?? undefined,
          })),
        }
      : { branchId: branches.items[0]?.id, documentDate: dayjs(), invoiceDate: dayjs(), lines: [] };

  const columns: TableColumnsType<PurchaseInvoice> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    {
      title: 'Hóa đơn',
      width: 170,
      render: (_, row) => `${row.invoiceSeries}-${row.invoiceNo}`,
    },
    { title: 'Ngày HĐ', dataIndex: 'invoiceDate', width: 110, render: formatDate },
    { title: 'Nhà cung cấp', render: (_, row) => `${row.supplierCode ?? ''} - ${row.supplierName ?? ''}` },
    { title: 'Hạn thanh toán', dataIndex: 'dueDate', width: 120, render: formatDate },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 150, align: 'right', render: formatMoney },
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
        Hóa đơn mua
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
            options={RECEIPT_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Nhập hóa đơn"
        onCreate={can('purchase-invoice:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseInvoice> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseInvoice>
          id={selected}
          queryKey="purchase-invoice"
          listKeys={['purchase-invoices', 'debt-items']}
          load={purchaseInvoiceApi.get}
          act={purchaseInvoiceApi.act}
          permission="purchase-invoice"
          actions={(document) => invoiceActions(document.status, can)}
          title={(document) => `Hóa đơn mua ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              {document.matchWarnings.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Đối chiếu ba chiều: có chênh lệch đơn giá vượt dung sai"
                  description={document.matchWarnings.join(' ')}
                />
              )}
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Nhà cung cấp">
                  {`${document.supplierCode ?? ''} - ${document.supplierName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="MST người bán">{document.sellerTaxCode}</Descriptions.Item>
                <Descriptions.Item label="Ký hiệu - số">
                  {`${document.invoiceSeries} - ${document.invoiceNo}`}
                </Descriptions.Item>
                <Descriptions.Item label="Ngày hóa đơn">{formatDate(document.invoiceDate)}</Descriptions.Item>
                <Descriptions.Item label="Hạn thanh toán">{formatDate(document.dueDate)}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng thanh toán">
                  <strong>{formatMoney(document.totalAmount)}</strong>
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={3}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<PurchaseInvoiceLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1300 }}
              />
              <SourceJournal sourceType="HDM" sourceId={document.id} status={document.status} />
            </>
          )}
        </PurchaseDocumentDrawer>
      )}
      <FormModal<InvoiceForm>
        open={editing !== null}
        title={editing === 'new' ? 'Nhập hóa đơn mua' : 'Sửa hóa đơn mua'}
        width={1200}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <InvoiceFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
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
  const supplierId = Form.useWatch('supplierId', form) as number | undefined;
  const invoiceable = useQuery({
    queryKey: ['purchase-invoices', 'invoiceable', branchId, supplierId],
    queryFn: () =>
      purchaseInvoiceApi.invoiceableLines({ branchId: branchId as number, supplierId: supplierId as number }),
    enabled: typeof branchId === 'number' && typeof supplierId === 'number',
  });
  const sourceLines = invoiceable.data ?? [];
  const lineLabel = (line: InvoiceableLine) =>
    `${line.receiptNo} dòng ${line.lineNo}: ${line.productCode ?? ''} - ${line.productName ?? ''} (còn ${formatNumber(line.remainingQuantity)} ${units.nameOf(line.unitId)})`;
  const addAllGoods = () => {
    const current = (form.getFieldValue('lines') as LineForm[] | undefined) ?? [];
    const used = new Set(current.map((line) => line.receiptLineId));
    const added: LineForm[] = sourceLines
      .filter((line) => !used.has(line.receiptLineId))
      .map((line) => ({
        kind: 'GOODS',
        receiptLineId: line.receiptLineId,
        quantity: line.remainingQuantity,
        unitPrice: line.unitPrice,
        taxRateId: line.taxRateId ?? undefined,
      }));
    form.setFieldValue('lines', [...current, ...added]);
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
          <Form.Item
            name="supplierId"
            label="Nhà cung cấp"
            rules={[{ required: true, message: 'Chọn nhà cung cấp' }]}
          >
            <PartnerSelect
              role="SUPPLIER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('supplierLabel')}
              onChange={() => form.setFieldValue('lines', [])}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={5}>
          <Form.Item name="sellerTaxCode" label="MST người bán" extra="Trống: theo nhà cung cấp">
            <Input maxLength={20} />
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
        <Col xs={12} md={4}>
          <Form.Item
            name="invoiceSeries"
            label="Ký hiệu"
            rules={[{ required: true, whitespace: true, message: 'Nhập ký hiệu' }]}
          >
            <Input maxLength={20} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name="invoiceNo"
            label="Số hóa đơn"
            rules={[{ required: true, whitespace: true, message: 'Nhập số' }]}
          >
            <Input maxLength={30} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name="invoiceDate"
            label="Ngày hóa đơn"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="dueDate" label="Hạn thanh toán" extra="Trống: theo đơn mua / NCC">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={4}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <InvoiceLineFields
                key={field.key}
                form={form}
                name={field.name}
                sourceOptions={sourceLines.map((line) => ({
                  value: line.receiptLineId,
                  label: lineLabel(line),
                }))}
                sourceLines={sourceLines}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Space wrap>
              <Button
                icon={<PlusOutlined />}
                disabled={sourceLines.length === 0}
                loading={invoiceable.isLoading}
                onClick={addAllGoods}
              >
                Thêm mọi dòng phiếu nhận chưa có hóa đơn
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => add({ kind: 'GOODS' })}>
                Thêm dòng hàng hóa
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => add({ kind: 'SERVICE' })}>
                Thêm dòng dịch vụ
              </Button>
            </Space>
          </>
        )}
      </Form.List>
    </>
  );
}

function InvoiceLineFields({
  form,
  name,
  sourceOptions,
  sourceLines,
  onRemove,
}: {
  form: FormInstance<InvoiceForm>;
  name: number;
  sourceOptions: { value: number; label: string }[];
  sourceLines: InvoiceableLine[];
  onRemove(): void;
}) {
  const taxRates = useTaxRates();
  const kind = Form.useWatch(['lines', name, 'kind'], form) as LineKind | undefined;
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const { options } = useProductUnits(kind === 'SERVICE' ? productId : undefined);
  const current = form.getFieldValue(['lines', name, 'receiptLineId']) as number | undefined;
  const goodsOptions =
    current && !sourceOptions.some((option) => option.value === current)
      ? [...sourceOptions, { value: current, label: `Dòng phiếu nhận #${current}` }]
      : sourceOptions;
  return (
    <Card size="small" style={{ marginBottom: 8 }} title={kind === 'SERVICE' ? 'Dịch vụ' : 'Hàng hóa'}>
      <Form.Item name={[name, 'kind']} hidden>
        <Input />
      </Form.Item>
      <Row gutter={8}>
        {kind === 'SERVICE' ? (
          <>
            <Col xs={24} md={8}>
              <Form.Item
                name={[name, 'productId']}
                label="Dịch vụ"
                rules={[{ required: true, message: 'Chọn dịch vụ' }]}
              >
                <ProductSelect
                  selectedLabel={form.getFieldValue(['lines', name, 'productLabel'])}
                  onProduct={(product) => {
                    if (product) {
                      form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId);
                      form.setFieldValue(['lines', name, 'taxRateId'], product.defaultTaxRateId ?? undefined);
                    }
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item
                name={[name, 'unitId']}
                label="Đơn vị"
                rules={[{ required: true, message: 'Chọn ĐVT' }]}
              >
                <Select options={options} />
              </Form.Item>
            </Col>
          </>
        ) : (
          <Col xs={24} md={11}>
            <Form.Item
              name={[name, 'receiptLineId']}
              label="Dòng phiếu nhận hàng"
              rules={[{ required: true, message: 'Chọn dòng phiếu nhận' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={goodsOptions}
                onChange={(value: number) => {
                  const source = sourceLines.find((line) => line.receiptLineId === value);
                  if (source) {
                    form.setFieldValue(['lines', name, 'quantity'], source.remainingQuantity);
                    form.setFieldValue(['lines', name, 'unitPrice'], source.unitPrice);
                    form.setFieldValue(['lines', name, 'taxRateId'], source.taxRateId ?? undefined);
                  }
                }}
              />
            </Form.Item>
          </Col>
        )}
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
        <Col xs={12} md={4}>
          <Form.Item name={[name, 'taxRateId']} label="Thuế suất">
            <Select allowClear options={taxRates.options} />
          </Form.Item>
        </Col>
        <Col xs={10} md={1}>
          <Form.Item name={[name, 'promotional']} valuePropName="checked" label="KM">
            <Checkbox />
          </Form.Item>
        </Col>
        <Col xs={2} md={1}>
          <Button style={{ marginTop: 30 }} icon={<DeleteOutlined />} onClick={onRemove} />
        </Col>
        <Col xs={24}>
          <Form.Item name={[name, 'description']} label="Ghi chú">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
    </Card>
  );
}
