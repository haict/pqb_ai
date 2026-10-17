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
import { useSearchParams } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { formatMoney } from '../../accounting/labels';
import { moneyInputProps } from '../../finance/components/debtInputs';
import { DocumentStatusTag, QualityTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { useMyBranches } from '../../system/lookups';
import { SourceJournal } from '../../purchasing/components/SourceJournal';
import { salesOrderApi, salesReturnApi } from '../api';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { POSTING_STATUSES, RETURN_ACTION_ORDER, RETURN_TYPES, returnActions } from '../labels';
import type {
  ReturnableSalesLine as ReturnableLine,
  SalesDocumentStatus as PurchaseDocumentStatus,
  SalesReturn as PurchaseReturn,
  SalesReturnLine as PurchaseReturnLine,
  SalesReturnType as PurchaseReturnType,
} from '../types';
import { DecimalInput } from '../../../shared/components/DecimalInput';
import { BranchCol } from '../../../shared/components/BranchCol';

interface LineForm {
  sourceKey?: string;
  quantity?: number;
  amount?: number;
  qualityStatus?: QualityStatus;
  serialNumbers?: string[];
  description?: string;
}

interface ReturnForm {
  documentType: PurchaseReturnType;
  branchId: number;
  customerId?: number;
  customerLabel?: string;
  salesOrderId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'documentType',
  'branchId',
  'customerId',
  'salesOrderId',
  'documentDate',
  'postingDate',
  'description',
  'lines',
];

const QUALITY_OPTIONS = (Object.keys(QUALITY_STATUSES) as QualityStatus[]).map((value) => ({
  value,
  label: QUALITY_STATUSES[value].label,
}));

/** Khóa dòng nguồn: dòng đơn bán kèm lô đã xuất. */
const sourceKey = (line: { orderLineId: number; lotNo: string | null }) =>
  `${line.orderLineId}|${line.lotNo ?? ''}`;

const parseKey = (key?: string) => {
  const [source = '', lot] = (key ?? '').split('|');
  return { id: Number(source), lotNo: lot || null };
};

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseReturnLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    { title: 'Lô', dataIndex: 'lotNo', width: 110 },
    {
      title: 'Nhập lại',
      dataIndex: 'qualityStatus',
      width: 110,
      render: (value: QualityStatus | null) => (value ? <QualityTag status={value} /> : null),
    },
    {
      title: 'Số lượng',
      width: 110,
      align: 'right',
      render: (_, row) =>
        row.quantity === null ? '' : `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 120, align: 'right', render: formatMoney },
    { title: 'Thành tiền', dataIndex: 'amount', width: 130, align: 'right', render: formatMoney },
    { title: 'Tiền thuế', dataIndex: 'taxAmount', width: 110, align: 'right', render: formatMoney },
    {
      title: 'Serial',
      dataIndex: 'serialNumbers',
      width: 160,
      render: (value: string[]) => value.join(', '),
    },
  ];
}

/**
 * Trả hàng bán / giảm giá hàng bán (BR-SAL-32, 33, 36, D-54) theo một đơn bán đã ghi sổ: trả đúng lô, serial đã xuất,
 * nhập lại kho cách ly; giảm doanh thu, công nợ phải thu; đổi hàng (lập đơn bán mới); ghi sổ, đảo.
 */
export function SalesReturnsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [documentType, setDocumentType] = useState<PurchaseReturnType>();
  // Mở thẳng chứng từ từ liên kết ?id= (truy ngược từ sổ chi tiết bán hàng, BR-RPT-11).
  const [searchParams] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(() => Number(searchParams.get('id')) || null);
  const [editing, setEditing] = useState<PurchaseReturn | 'new' | null>(null);
  const { tableProps } = useServerTable('sales-returns', salesReturnApi.list, {
    companyId,
    keyword,
    status,
    documentType,
  });

  const save = async (values: ReturnForm, key: string) => {
    const goods = values.documentType === 'TLB';
    const input = {
      documentType: values.documentType,
      branchId: values.branchId,
      customerId: values.customerId,
      salesOrderId: values.salesOrderId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => {
        const parsed = parseKey(line.sourceKey);
        return {
          orderLineId: parsed.id,
          lotNo: goods ? parsed.lotNo : null,
          quantity: goods ? line.quantity : null,
          amount: goods ? null : line.amount,
          qualityStatus: goods ? (line.qualityStatus ?? null) : null,
          serialNumbers: goods ? (line.serialNumbers ?? []) : [],
          description: line.description,
        };
      }),
    };
    if (editing === 'new') {
      const created = await salesReturnApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await salesReturnApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['sales-return', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['sales-returns'] });
  };

  const initialValues: Partial<ReturnForm> =
    editing && editing !== 'new'
      ? {
          documentType: editing.documentType,
          branchId: editing.branchId,
          customerId: editing.customerId,
          customerLabel: `${editing.customerCode ?? ''} - ${editing.customerName ?? ''}`,
          salesOrderId: editing.salesOrderId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            sourceKey: sourceKey(line),
            quantity: line.quantity ?? undefined,
            amount: line.amount,
            serialNumbers: line.serialNumbers,
            description: line.description ?? undefined,
          })),
        }
      : { documentType: 'TLB', branchId: branches.defaultId, documentDate: dayjs(), lines: [] };

  const columns: TableColumnsType<PurchaseReturn> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    {
      title: 'Loại',
      dataIndex: 'documentType',
      width: 190,
      render: (value: PurchaseReturnType) => RETURN_TYPES[value],
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    { title: 'Nhà cung cấp', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
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
        Trả hàng / giảm giá hàng bán
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Space wrap>
            <Select
              allowClear
              placeholder="Loại"
              style={{ width: 200 }}
              value={documentType}
              onChange={setDocumentType}
              options={(Object.keys(RETURN_TYPES) as PurchaseReturnType[]).map((value) => ({
                value,
                label: RETURN_TYPES[value],
              }))}
            />
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 160 }}
              value={status}
              onChange={setStatus}
              options={POSTING_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
          </Space>
        }
        createLabel="Lập chứng từ"
        onCreate={can('sales-return:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseReturn> {...tableProps} size="small" columns={columns} scroll={{ x: 1000 }} />
      {selected !== null && (
        <SalesDocumentDrawer<PurchaseReturn>
          id={selected}
          queryKey="sales-return"
          listKeys={['sales-returns', 'sales-orders', 'debt-items']}
          load={salesReturnApi.get}
          act={salesReturnApi.act}
          permission="sales-return"
          actions={(document) => returnActions(document, can)}
          actionOrder={RETURN_ACTION_ORDER}
          title={(document) => `${RETURN_TYPES[document.documentType]} ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Khách hàng">
                  {`${document.customerCode ?? ''} - ${document.customerName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Đơn bán">
                  {`${document.salesOrderNo ?? ''} (${document.invoiceLabel ?? ''})`}
                </Descriptions.Item>
                <Descriptions.Item label="Phiếu nhập lại">{document.inventoryDocumentNo}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Tiền hàng">{formatMoney(document.amount)}</Descriptions.Item>
                <Descriptions.Item label="Thuế GTGT">{formatMoney(document.taxAmount)}</Descriptions.Item>
                <Descriptions.Item label="Tổng giảm">
                  <strong>{formatMoney(document.totalAmount)}</strong>
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={3}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<PurchaseReturnLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1200 }}
              />
              <SourceJournal
                sourceType={document.documentType}
                sourceId={document.id}
                status={document.status}
              />
            </>
          )}
        </SalesDocumentDrawer>
      )}
      <FormModal<ReturnForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập trả hàng / giảm giá hàng bán' : 'Sửa chứng từ'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <ReturnFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
    </Card>
  );
}

function ReturnFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<ReturnForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const companyId = useCompanyId();
  const units = useCatalog('units');
  const documentType = Form.useWatch('documentType', form) as PurchaseReturnType | undefined;
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const customerId = Form.useWatch('customerId', form) as number | undefined;
  const orderId = Form.useWatch('salesOrderId', form) as number | undefined;
  const ready = typeof branchId === 'number' && typeof customerId === 'number';
  const orders = useQuery({
    queryKey: ['sales-orders', 'posted', branchId, customerId],
    queryFn: () =>
      salesOrderApi.list({ page: 0, size: 50, companyId, branchId, customerId, status: 'POSTED' }),
    enabled: ready,
  });
  const returnable = useQuery({
    queryKey: ['sales-returns', 'returnable', branchId, customerId, orderId],
    queryFn: () =>
      salesReturnApi.returnableLines({
        branchId: branchId as number,
        customerId: customerId as number,
        orderId: orderId as number,
      }),
    enabled: ready && typeof orderId === 'number',
  });
  const sources = (returnable.data ?? []).filter((line) =>
    documentType === 'GGB' ? line.remainingAmount > 0 : !line.serviceLine && line.remainingQuantity > 0,
  );
  const sourceLabel = (line: ReturnableLine) =>
    `${line.productCode ?? ''} - ${line.productName ?? ''}${line.lotNo ? ` · lô ${line.lotNo}` : ''} (${
      documentType === 'GGB'
        ? `còn ${formatMoney(line.remainingAmount)}`
        : `còn ${formatNumber(line.remainingQuantity)} ${units.nameOf(line.unitId)}`
    })`;
  const resetLines = () => form.setFieldValue('lines', []);
  const addAll = () => {
    const current = (form.getFieldValue('lines') as LineForm[] | undefined) ?? [];
    const used = new Set(current.map((line) => line.sourceKey));
    form.setFieldValue('lines', [
      ...current,
      ...sources
        .filter((line) => !used.has(sourceKey(line)))
        .map((line) => ({
          sourceKey: sourceKey(line),
          quantity: line.remainingQuantity,
          amount: line.remainingAmount,
        })),
    ]);
  };
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={6}>
          <Form.Item name="documentType" label="Loại" rules={[{ required: true }]}>
            <Select
              disabled={!isNew}
              onChange={resetLines}
              options={(Object.keys(RETURN_TYPES) as PurchaseReturnType[]).map((value) => ({
                value,
                label: RETURN_TYPES[value],
              }))}
            />
          </Form.Item>
        </Col>
        <BranchCol xs={24} md={5}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} onChange={resetLines} />
          </Form.Item>
        </BranchCol>
        <Col xs={24} md={7}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách' }]}>
            <PartnerSelect
              role="CUSTOMER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('customerLabel')}
              onChange={() => {
                form.setFieldValue('salesOrderId', undefined);
                resetLines();
              }}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="salesOrderId"
            label="Đơn bán đã ghi sổ"
            rules={[{ required: true, message: 'Chọn đơn bán' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!isNew}
              loading={orders.isLoading}
              onChange={resetLines}
              options={(orders.data?.items ?? []).map((order) => ({
                value: order.id,
                label: `${order.documentNo}${order.einvoiceNo ? ` (${order.einvoiceSeries}-${order.einvoiceNo})` : ''}`,
              }))}
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
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={14}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <ReturnLineFields
                key={field.key}
                form={form}
                name={field.name}
                goods={documentType !== 'GGB'}
                sources={sources}
                options={sources.map((line) => ({ value: sourceKey(line), label: sourceLabel(line) }))}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Space wrap>
              <Button icon={<PlusOutlined />} disabled={sources.length === 0} onClick={addAll}>
                Thêm mọi dòng còn lại
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

function ReturnLineFields({
  form,
  name,
  goods,
  sources,
  options,
  onRemove,
}: {
  form: FormInstance<ReturnForm>;
  name: number;
  goods: boolean;
  sources: ReturnableLine[];
  options: { value: string; label: string }[];
  onRemove(): void;
}) {
  const key = Form.useWatch(['lines', name, 'sourceKey'], form) as string | undefined;
  const source = sources.find((line) => sourceKey(line) === key);
  const current = form.getFieldValue(['lines', name, 'sourceKey']) as string | undefined;
  const allOptions =
    current && !options.some((option) => option.value === current)
      ? [...options, { value: current, label: `Dòng nguồn ${current}` }]
      : options;
  return (
    <Card size="small" style={{ marginBottom: 8 }}>
      <Row gutter={8}>
        <Col xs={24} md={goods ? 10 : 14}>
          <Form.Item
            name={[name, 'sourceKey']}
            label={goods ? 'Dòng đơn bán (lô đã xuất)' : 'Dòng đơn bán'}
            rules={[{ required: true, message: 'Chọn dòng nguồn' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={allOptions}
              onChange={(value: string) => {
                const picked = sources.find((line) => sourceKey(line) === value);
                if (picked) {
                  form.setFieldValue(['lines', name, 'quantity'], picked.remainingQuantity);
                  form.setFieldValue(['lines', name, 'amount'], picked.remainingAmount);
                  form.setFieldValue(['lines', name, 'serialNumbers'], []);
                }
              }}
            />
          </Form.Item>
        </Col>
        {goods ? (
          <>
            <Col xs={12} md={3}>
              <Form.Item
                name={[name, 'quantity']}
                label="Số lượng trả"
                rules={[{ required: true, message: 'Nhập số lượng' }]}
              >
                <DecimalInput min={0.001} />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name={[name, 'qualityStatus']} label="Nhập lại trạng thái" extra="Trống: cách ly">
                <Select allowClear options={QUALITY_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={22} md={6}>
              <Form.Item name={[name, 'serialNumbers']} label="Serial (hàng quản lý serial)">
                <Select
                  mode="multiple"
                  options={(source?.serialNumbers ?? []).map((serial) => ({ value: serial, label: serial }))}
                />
              </Form.Item>
            </Col>
          </>
        ) : (
          <Col xs={22} md={9}>
            <Form.Item
              name={[name, 'amount']}
              label="Số tiền giảm (trước thuế)"
              rules={[{ required: true, message: 'Nhập số tiền' }]}
            >
              <InputNumber<number> min={1} {...moneyInputProps} />
            </Form.Item>
          </Col>
        )}
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
