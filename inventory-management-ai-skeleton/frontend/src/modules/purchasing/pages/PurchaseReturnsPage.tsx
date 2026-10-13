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
import { DocumentStatusTag, QualityTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { useMyBranches } from '../../system/lookups';
import { purchaseOrderApi, purchaseReturnApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { SourceJournal } from '../components/SourceJournal';
import { postingActions, PURCHASE_STATUSES, RETURN_TYPES } from '../labels';
import type {
  PurchaseDocumentStatus,
  PurchaseReturn,
  PurchaseReturnLine,
  PurchaseReturnType,
  ReturnableLine,
} from '../types';
import { DecimalInput } from '../../../shared/components/DecimalInput';

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
  supplierId?: number;
  supplierLabel?: string;
  purchaseOrderId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'documentType',
  'branchId',
  'supplierId',
  'purchaseOrderId',
  'documentDate',
  'postingDate',
  'description',
  'lines',
];

const QUALITY_OPTIONS = (Object.keys(QUALITY_STATUSES) as QualityStatus[]).map((value) => ({
  value,
  label: QUALITY_STATUSES[value].label,
}));

/** Khóa dòng nguồn: id dòng đơn mua. */
const sourceKey = (line: { orderLineId: number }) => String(line.orderLineId);

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseReturnLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    { title: 'Lô', dataIndex: 'lotNo', width: 110 },
    {
      title: 'Chất lượng',
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
 * Trả hàng nhà cung cấp / giảm giá hàng mua (BR-PUR-14, D-54) theo một đơn mua đã ghi sổ: trả đúng lô, serial của
 * dòng đơn; trả hàng và giảm giá giảm công nợ phải trả; ghi sổ, đảo.
 */
export function PurchaseReturnsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [documentType, setDocumentType] = useState<PurchaseReturnType>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseReturn | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-returns', purchaseReturnApi.list, {
    companyId,
    keyword,
    status,
    documentType,
  });

  const save = async (values: ReturnForm, key: string) => {
    const goods = values.documentType === 'THN';
    const input = {
      documentType: values.documentType,
      branchId: values.branchId,
      supplierId: values.supplierId,
      purchaseOrderId: values.purchaseOrderId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        orderLineId: Number(line.sourceKey),
        quantity: goods ? line.quantity : null,
        amount: goods ? null : line.amount,
        qualityStatus: goods ? (line.qualityStatus ?? null) : null,
        serialNumbers: goods ? (line.serialNumbers ?? []) : [],
        description: line.description,
      })),
    };
    if (editing === 'new') {
      const created = await purchaseReturnApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await purchaseReturnApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-return', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-returns'] });
  };

  const initialValues: Partial<ReturnForm> =
    editing && editing !== 'new'
      ? {
          documentType: editing.documentType,
          branchId: editing.branchId,
          supplierId: editing.supplierId,
          supplierLabel: `${editing.supplierCode ?? ''} - ${editing.supplierName ?? ''}`,
          purchaseOrderId: editing.purchaseOrderId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            sourceKey: sourceKey(line),
            quantity: line.quantity ?? undefined,
            amount: line.amount,
            qualityStatus: line.qualityStatus ?? undefined,
            serialNumbers: line.serialNumbers,
            description: line.description ?? undefined,
          })),
        }
      : { documentType: 'THN', branchId: branches.items[0]?.id, documentDate: dayjs(), lines: [] };

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
    { title: 'Nhà cung cấp', render: (_, row) => `${row.supplierCode ?? ''} - ${row.supplierName ?? ''}` },
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
        Trả hàng / giảm giá hàng mua
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
              options={PURCHASE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
          </Space>
        }
        createLabel="Lập chứng từ"
        onCreate={can('purchase-return:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseReturn> {...tableProps} size="small" columns={columns} scroll={{ x: 1000 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseReturn>
          id={selected}
          queryKey="purchase-return"
          listKeys={['purchase-returns', 'purchase-orders', 'debt-items']}
          load={purchaseReturnApi.get}
          act={purchaseReturnApi.act}
          permission="purchase-return"
          actions={(document) => postingActions(document.status, 'purchase-return', can)}
          title={(document) => `${RETURN_TYPES[document.documentType]} ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Nhà cung cấp">
                  {`${document.supplierCode ?? ''} - ${document.supplierName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Đơn mua">
                  {`${document.purchaseOrderNo ?? ''} (${document.invoiceLabel ?? ''})`}
                </Descriptions.Item>
                <Descriptions.Item label="Phiếu xuất trả">{document.inventoryDocumentNo}</Descriptions.Item>
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
        </PurchaseDocumentDrawer>
      )}
      <FormModal<ReturnForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập trả hàng / giảm giá hàng mua' : 'Sửa chứng từ'}
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
  const supplierId = Form.useWatch('supplierId', form) as number | undefined;
  const orderId = Form.useWatch('purchaseOrderId', form) as number | undefined;
  const ready = typeof branchId === 'number' && typeof supplierId === 'number';
  const orders = useQuery({
    queryKey: ['purchase-orders', 'posted', branchId, supplierId],
    queryFn: () =>
      purchaseOrderApi.list({ page: 0, size: 50, companyId, branchId, supplierId, status: 'POSTED' }),
    enabled: ready,
  });
  const returnable = useQuery({
    queryKey: ['purchase-returns', 'returnable', branchId, supplierId, orderId],
    queryFn: () =>
      purchaseReturnApi.returnableLines({
        branchId: branchId as number,
        supplierId: supplierId as number,
        orderId: orderId as number,
      }),
    enabled: ready && typeof orderId === 'number',
  });
  const sources = (returnable.data ?? []).filter((line) =>
    documentType === 'GGM' ? line.remainingAmount > 0 : !line.serviceLine && line.remainingQuantity > 0,
  );
  const sourceLabel = (line: ReturnableLine) =>
    `${line.productCode ?? ''} - ${line.productName ?? ''}${line.lotNo ? ` · lô ${line.lotNo}` : ''} (${
      documentType === 'GGM'
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
          qualityStatus: line.qualityStatus ?? undefined,
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
              onChange={() => {
                resetLines();
              }}
              options={(Object.keys(RETURN_TYPES) as PurchaseReturnType[]).map((value) => ({
                value,
                label: RETURN_TYPES[value],
              }))}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={5}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} onChange={resetLines} />
          </Form.Item>
        </Col>
        <Col xs={24} md={7}>
          <Form.Item name="supplierId" label="Nhà cung cấp" rules={[{ required: true, message: 'Chọn NCC' }]}>
            <PartnerSelect
              role="SUPPLIER"
              disabled={!isNew}
              selectedLabel={form.getFieldValue('supplierLabel')}
              onChange={() => {
                form.setFieldValue('purchaseOrderId', undefined);
                resetLines();
              }}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="purchaseOrderId"
            label="Đơn mua đã ghi sổ"
            rules={[{ required: true, message: 'Chọn đơn mua' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!isNew}
              loading={orders.isLoading}
              onChange={resetLines}
              options={(orders.data?.items ?? []).map((order) => ({
                value: order.id,
                label: `${order.documentNo}${order.invoiceNo ? ` (${order.invoiceSeries}-${order.invoiceNo})` : ''}`,
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
                goods={documentType !== 'GGM'}
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
            label="Dòng đơn mua"
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
                  form.setFieldValue(['lines', name, 'qualityStatus'], picked.qualityStatus ?? undefined);
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
              <Form.Item name={[name, 'qualityStatus']} label="Chất lượng hàng trả">
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
