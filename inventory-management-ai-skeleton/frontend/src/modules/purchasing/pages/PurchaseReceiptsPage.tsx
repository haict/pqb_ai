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
  Table,
  Tag,
  Typography,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { formatMoney } from '../../accounting/labels';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { purchaseOrderApi, purchaseReceiptApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { INSPECTION_RESULTS, RECEIPT_ACTION_ORDER, RECEIPT_STATUSES, receiptActions } from '../labels';
import type {
  InspectionResult,
  PurchaseDocumentStatus,
  PurchaseReceipt,
  PurchaseReceiptLine,
} from '../types';

interface LineForm {
  orderLineId?: number;
  quantity?: number;
  inspectionResult?: InspectionResult;
  rejectReason?: string;
  lotNo?: string;
  manufactureDate?: Dayjs;
  expiryDate?: Dayjs;
  serialNumbers?: string[];
  note?: string;
}

interface ReceiptForm {
  purchaseOrderId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  deliveryNoteNo?: string;
  description?: string;
  stockNow?: boolean;
  lines: LineForm[];
}

const FIELDS = ['purchaseOrderId', 'documentDate', 'postingDate', 'deliveryNoteNo', 'description', 'lines'];

const RESULT_COLORS: Record<InspectionResult, string> = {
  ACCEPTED: 'green',
  QUARANTINE: 'orange',
  RETURN: 'red',
};

const dateValue = (value?: Dayjs) => (value ? value.format('YYYY-MM-DD') : null);

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseReceiptLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    {
      title: 'Số lượng',
      width: 120,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    {
      title: 'Kết quả',
      dataIndex: 'inspectionResult',
      width: 190,
      render: (value: InspectionResult) => (
        <Tag color={RESULT_COLORS[value]}>{INSPECTION_RESULTS[value]}</Tag>
      ),
    },
    { title: 'Lý do lỗi', dataIndex: 'rejectReason', width: 160 },
    { title: 'Lô', dataIndex: 'lotNo', width: 110 },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', width: 110, render: formatDate },
    {
      title: 'Serial',
      dataIndex: 'serialNumbers',
      width: 160,
      render: (value: string[]) => value.join(', '),
    },
    { title: 'Đơn giá tạm tính', dataIndex: 'unitCost', width: 130, align: 'right', render: formatMoney },
  ];
}

/**
 * Phiếu nhận hàng (PNH, BR-PUR-07, BR-PUR-08, D-36): nhận theo đơn mua, kiểm nhận đạt / cách ly / trả lại, nhập kho
 * thành phiếu nhập kho riêng (có thể ngay khi lập), đảo.
 */
export function PurchaseReceiptsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseReceipt | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-receipts', purchaseReceiptApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: ReceiptForm, key: string) => {
    const input = {
      purchaseOrderId: values.purchaseOrderId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: dateValue(values.postingDate),
      deliveryNoteNo: values.deliveryNoteNo,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        orderLineId: line.orderLineId,
        quantity: line.quantity,
        inspectionResult: line.inspectionResult,
        rejectReason: line.rejectReason,
        lotNo: line.lotNo,
        manufactureDate: dateValue(line.manufactureDate),
        expiryDate: dateValue(line.expiryDate),
        serialNumbers: line.serialNumbers ?? [],
        note: line.note,
      })),
    };
    if (editing === 'new') {
      const created = values.stockNow
        ? await purchaseReceiptApi.receiveAndStock(input, key)
        : await purchaseReceiptApi.create(input, key);
      message.success(
        created.stockedAt
          ? `Đã nhận hàng và nhập kho ${created.documentNo}.`
          : `Đã lập ${created.documentNo} (${statusLabel(created.status)}).`,
      );
      setSelected(created.id);
    } else if (editing) {
      await purchaseReceiptApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu phiếu nhận hàng.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-receipt', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-receipts'] });
    await queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
  };

  const initialValues: Partial<ReceiptForm> =
    editing && editing !== 'new'
      ? {
          purchaseOrderId: editing.purchaseOrderId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          deliveryNoteNo: editing.deliveryNoteNo ?? undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            orderLineId: line.orderLineId,
            quantity: line.quantity,
            inspectionResult: line.inspectionResult,
            rejectReason: line.rejectReason ?? undefined,
            lotNo: line.lotNo ?? undefined,
            manufactureDate: line.manufactureDate ? dayjs(line.manufactureDate) : undefined,
            expiryDate: line.expiryDate ? dayjs(line.expiryDate) : undefined,
            serialNumbers: line.serialNumbers,
            note: line.note ?? undefined,
          })),
        }
      : { documentDate: dayjs(), stockNow: true, lines: [{ inspectionResult: 'ACCEPTED' }] };

  const columns: TableColumnsType<PurchaseReceipt> = [
    {
      title: 'Số phiếu',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    { title: 'Đơn mua', dataIndex: 'purchaseOrderNo', width: 180 },
    { title: 'Nhà cung cấp', render: (_, row) => `${row.supplierCode ?? ''} - ${row.supplierName ?? ''}` },
    { title: 'Kho', dataIndex: 'warehouseName', width: 160 },
    { title: 'Phiếu nhập kho', dataIndex: 'inventoryDocumentNo', width: 180 },
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
        Phiếu nhận hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số phiếu, diễn giải"
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
        createLabel="Nhận hàng"
        onCreate={can('purchase-receipt:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseReceipt> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseReceipt>
          id={selected}
          queryKey="purchase-receipt"
          listKeys={['purchase-receipts', 'purchase-orders', 'purchase-order', 'stock-summaries']}
          load={purchaseReceiptApi.get}
          act={purchaseReceiptApi.act}
          permission="purchase-receipt"
          actions={(document) => receiptActions(document, can)}
          actionOrder={RECEIPT_ACTION_ORDER}
          title={(document) => `Phiếu nhận hàng ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              {document.overTolerance && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Nhận vượt số đặt quá dung sai: phiếu phải được duyệt trước khi ghi sổ."
                />
              )}
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Đơn mua">{document.purchaseOrderNo}</Descriptions.Item>
                <Descriptions.Item label="Nhà cung cấp">
                  {`${document.supplierCode ?? ''} - ${document.supplierName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Kho">{document.warehouseName}</Descriptions.Item>
                <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Phiếu giao NCC">{document.deliveryNoteNo}</Descriptions.Item>
                <Descriptions.Item label="Phiếu nhập kho">
                  {document.inventoryDocumentId ? (
                    <Link to={`/inventory/documents/${document.inventoryDocumentId}`}>
                      {document.inventoryDocumentNo}
                    </Link>
                  ) : document.stockedAt ? (
                    'Không có hàng nhập kho'
                  ) : (
                    'Chưa nhập kho'
                  )}
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={3}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<PurchaseReceiptLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1300 }}
              />
            </>
          )}
        </PurchaseDocumentDrawer>
      )}
      <FormModal<ReceiptForm>
        open={editing !== null}
        title={editing === 'new' ? 'Nhận hàng theo đơn mua' : 'Sửa phiếu nhận hàng'}
        width={1200}
        okText="Lưu"
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <ReceiptFields form={form} isNew={editing === 'new'} companyId={companyId} />}
      </FormModal>
    </Card>
  );
}

function ReceiptFields({
  form,
  isNew,
  companyId,
}: {
  form: FormInstance<ReceiptForm>;
  isNew: boolean;
  companyId: number;
}) {
  const units = useCatalog('units');
  const orderId = Form.useWatch('purchaseOrderId', form) as number | undefined;
  const openOrders = useQuery({
    queryKey: ['purchase-orders', 'open', companyId],
    queryFn: () => purchaseOrderApi.list({ page: 0, size: 200, companyId, openOnly: true }),
    enabled: isNew,
  });
  const order = useQuery({
    queryKey: ['purchase-order', orderId],
    queryFn: () => purchaseOrderApi.get(orderId as number),
    enabled: typeof orderId === 'number',
  }).data;
  const orderOptions = isNew
    ? (openOrders.data?.items ?? []).map((item) => ({
        value: item.id,
        label: `${item.documentNo} - ${item.supplierName ?? ''} (${item.warehouseName ?? ''})`,
      }))
    : order
      ? [{ value: order.id, label: order.documentNo }]
      : [];
  const lineOptions = (order?.lines ?? []).map((line) => ({
    value: line.id,
    label: `${line.lineNo}. ${line.productCode ?? ''} - ${line.productName ?? ''} (đặt ${formatNumber(line.quantity)} ${units.nameOf(line.unitId)}, còn thiếu ${formatNumber(line.outstandingQuantity)} ${units.nameOf(line.baseUnitId)})`,
  }));
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={10}>
          <Form.Item
            name="purchaseOrderId"
            label="Đơn mua"
            rules={[{ required: true, message: 'Chọn đơn mua' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!isNew}
              options={orderOptions}
              loading={openOrders.isLoading}
              onChange={() => form.setFieldsValue({ lines: [{ inspectionResult: 'ACCEPTED' }] })}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="documentDate" label="Ngày nhận" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item name="deliveryNoteNo" label="Số phiếu giao của NCC">
            <Input maxLength={50} />
          </Form.Item>
        </Col>
        <Col xs={24} md={isNew ? 16 : 24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        {isNew && (
          <Col xs={24} md={8}>
            <Form.Item
              name="stockNow"
              valuePropName="checked"
              label=" "
              extra="Không vượt dung sai thì ghi sổ và lập phiếu nhập kho ngay (D-36)"
            >
              <Checkbox>Nhập kho ngay</Checkbox>
            </Form.Item>
          </Col>
        )}
      </Row>
      {order && order.status !== 'APPROVED' && order.status !== 'IN_PROGRESS' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="Đơn mua không còn mở để nhận hàng."
        />
      )}
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <ReceiptLineFields
                key={field.key}
                form={form}
                name={field.name}
                lineOptions={lineOptions}
                removable={fields.length > 1}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Button
              type="dashed"
              block
              icon={<PlusOutlined />}
              onClick={() => add({ inspectionResult: 'ACCEPTED' })}
            >
              Thêm dòng (lô / kết quả khác)
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}

function ReceiptLineFields({
  form,
  name,
  lineOptions,
  removable,
  onRemove,
}: {
  form: FormInstance<ReceiptForm>;
  name: number;
  lineOptions: { value: number; label: string }[];
  removable: boolean;
  onRemove(): void;
}) {
  const result = Form.useWatch(['lines', name, 'inspectionResult'], form) as InspectionResult | undefined;
  const stocked = result !== 'RETURN';
  return (
    <Card size="small" style={{ marginBottom: 8 }}>
      <Row gutter={8}>
        <Col xs={24} md={10}>
          <Form.Item
            name={[name, 'orderLineId']}
            label="Dòng đơn mua"
            rules={[{ required: true, message: 'Chọn dòng' }]}
          >
            <Select showSearch optionFilterProp="label" options={lineOptions} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item
            name={[name, 'quantity']}
            label="Số lượng (ĐVT đơn mua)"
            rules={[{ required: true, message: 'Nhập số lượng' }]}
          >
            <InputNumber min={0.001} precision={3} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={5}>
          <Form.Item
            name={[name, 'inspectionResult']}
            label="Kết quả kiểm nhận"
            rules={[{ required: true, message: 'Chọn kết quả' }]}
          >
            <Select
              options={Object.entries(INSPECTION_RESULTS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Col>
        <Col xs={22} md={5}>
          <Form.Item
            name={[name, 'rejectReason']}
            label="Lý do lỗi"
            rules={[{ required: result !== undefined && result !== 'ACCEPTED', message: 'Nhập lý do' }]}
          >
            <Input maxLength={500} disabled={result === 'ACCEPTED'} />
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
        {stocked && (
          <>
            <Col xs={12} md={4}>
              <Form.Item name={[name, 'lotNo']} label="Số lô">
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item name={[name, 'manufactureDate']} label="Ngày sản xuất">
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item name={[name, 'expiryDate']} label="Hạn dùng">
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item
                name={[name, 'serialNumbers']}
                label="Serial (hàng quản lý serial)"
                extra="Gõ serial rồi Enter; số serial = số lượng theo đơn vị cơ sở"
              >
                <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
              </Form.Item>
            </Col>
          </>
        )}
        <Col xs={12} md={stocked ? 6 : 24}>
          <Form.Item name={[name, 'note']} label="Ghi chú">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
    </Card>
  );
}
