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
  Tag,
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
import { DocumentStatusTag, QualityTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import { salesDeliveryApi, salesOrderApi } from '../api';
import { SalesDocumentDrawer } from '../components/SalesDocumentDrawer';
import { PrintButton } from '../components/SalesLinesTable';
import { DELIVERY_ACTION_ORDER, DELIVERY_STATUSES, deliveryActions, POSTING_STATUSES } from '../labels';
import type {
  DeliveryStatus,
  SalesDelivery,
  SalesDeliveryLine,
  SalesDocumentStatus,
  SalesOrder,
} from '../types';

interface LineForm {
  orderLineId?: number;
  quantity?: number;
  lotNo?: string;
  lotOverrideReason?: string;
  serialNumbers?: string[];
  note?: string;
}

interface DeliveryForm {
  orderId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  deliveryAddress?: string;
  receiverName?: string;
  receiverPhone?: string;
  shipperName?: string;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'orderId',
  'documentDate',
  'postingDate',
  'deliveryAddress',
  'receiverName',
  'receiverPhone',
  'shipperName',
  'description',
  'lines',
];

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<SalesDeliveryLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    {
      title: 'Số lượng',
      width: 110,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    {
      title: 'Hàng đã xuất (lô / hạn / serial)',
      render: (_, row) =>
        row.items.length === 0
          ? row.lotNo
            ? `Lô chỉ định ${row.lotNo}`
            : 'FEFO khi xuất kho'
          : row.items
              .map(
                (item) =>
                  `${item.lotNo ?? ''}${item.expiryDate ? ` (HD ${formatDate(item.expiryDate)})` : ''}${
                    item.serialNo ? ` #${item.serialNo}` : ''
                  }: ${formatNumber(item.baseQuantity)}`,
              )
              .join('; '),
    },
    { title: 'Số lưu hành', dataIndex: 'registrationNo', width: 140 },
    {
      title: 'Đã HĐ / trả',
      width: 110,
      render: (_, row) => `${formatNumber(row.invoicedQuantity)} / ${formatNumber(row.returnedQuantity)}`,
    },
  ];
}

/**
 * Giao hàng theo đơn bán (BR-SAL-20..23): lập phiếu giao, xuất kho FEFO dùng hàng đã giữ, đang giao, khách nhận (biên
 * bản giao nhận), giao thất bại (hàng về kho), đảo; in biên bản giao nhận kèm lô, hạn dùng, serial, số lưu hành.
 */
export function SalesDeliveriesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SalesDocumentStatus>();
  const [deliveryStatus, setDeliveryStatus] = useState<DeliveryStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<SalesDelivery | 'new' | null>(null);
  const [confirming, setConfirming] = useState<SalesDelivery | null>(null);
  const { tableProps } = useServerTable('sales-deliveries', salesDeliveryApi.list, {
    companyId,
    keyword,
    status,
    deliveryStatus,
  });

  const save = async (values: DeliveryForm, key: string) => {
    const input = {
      orderId: values.orderId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      deliveryAddress: values.deliveryAddress,
      receiverName: values.receiverName,
      receiverPhone: values.receiverPhone,
      shipperName: values.shipperName,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({ ...line, serialNumbers: line.serialNumbers ?? [] })),
    };
    if (editing === 'new') {
      const created = await salesDeliveryApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await salesDeliveryApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu phiếu giao.');
      void queryClient.invalidateQueries({ queryKey: ['sales-delivery', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['sales-deliveries'] });
  };

  const initialValues: Partial<DeliveryForm> =
    editing && editing !== 'new'
      ? {
          orderId: editing.orderId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          deliveryAddress: editing.deliveryAddress ?? undefined,
          receiverName: editing.receiverName ?? undefined,
          receiverPhone: editing.receiverPhone ?? undefined,
          shipperName: editing.shipperName ?? undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            orderLineId: line.orderLineId,
            quantity: line.quantity,
            lotNo: line.lotNo ?? undefined,
            lotOverrideReason: line.lotOverrideReason ?? undefined,
            serialNumbers: line.serialNumbers,
            note: line.note ?? undefined,
          })),
        }
      : { documentDate: dayjs(), lines: [] };

  const columns: TableColumnsType<SalesDelivery> = [
    {
      title: 'Số phiếu',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    { title: 'Khách hàng', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
    {
      title: 'Giao hàng',
      dataIndex: 'deliveryStatus',
      width: 130,
      render: (value: DeliveryStatus, row) =>
        row.status === 'POSTED' || value === 'FAILED' ? (
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
        Giao hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số phiếu, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Space wrap>
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 160 }}
              value={status}
              onChange={setStatus}
              options={POSTING_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
            <Select
              allowClear
              placeholder="Giao hàng"
              style={{ width: 150 }}
              value={deliveryStatus}
              onChange={setDeliveryStatus}
              options={(Object.keys(DELIVERY_STATUSES) as DeliveryStatus[]).map((value) => ({
                value,
                label: DELIVERY_STATUSES[value].label,
              }))}
            />
          </Space>
        }
        createLabel="Lập phiếu giao"
        onCreate={can('sales-delivery:create') ? () => setEditing('new') : undefined}
      />
      <Table<SalesDelivery> {...tableProps} size="small" columns={columns} scroll={{ x: 900 }} />
      {selected !== null && (
        <SalesDocumentDrawer<SalesDelivery>
          id={selected}
          queryKey="sales-delivery"
          listKeys={['sales-deliveries', 'sales-orders', 'sales-order']}
          load={salesDeliveryApi.get}
          act={salesDeliveryApi.act}
          permission="sales-delivery"
          actions={(document) => deliveryActions(document, can)}
          actionOrder={DELIVERY_ACTION_ORDER}
          title={(document) => `Phiếu giao ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
          extra={(document) => (
            <>
              {document.status === 'POSTED' && (
                <PrintButton
                  label="In biên bản giao nhận"
                  title={`Biên bản giao nhận ${document.documentNo}`}
                >
                  <DeliveryNote delivery={document} unitName={units.nameOf} />
                </PrintButton>
              )}
              {document.status === 'POSTED' &&
                (document.deliveryStatus === 'PENDING' || document.deliveryStatus === 'IN_TRANSIT') &&
                can('sales-delivery:deliver') && (
                  <Button type="primary" onClick={() => setConfirming(document)}>
                    Khách đã nhận
                  </Button>
                )}
            </>
          )}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Đơn bán">{document.orderNo}</Descriptions.Item>
                <Descriptions.Item label="Khách hàng">
                  {`${document.customerCode ?? ''} - ${document.customerName ?? ''}`}
                </Descriptions.Item>
                <Descriptions.Item label="Kho xuất">{document.warehouseCode}</Descriptions.Item>
                <Descriptions.Item label="Phiếu xuất kho">{document.inventoryDocumentNo}</Descriptions.Item>
                <Descriptions.Item label="Giao hàng">
                  <Tag color={DELIVERY_STATUSES[document.deliveryStatus].color}>
                    {DELIVERY_STATUSES[document.deliveryStatus].label}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Vận chuyển">{document.shipperName}</Descriptions.Item>
                <Descriptions.Item label="Địa chỉ giao">{document.deliveryAddress}</Descriptions.Item>
                <Descriptions.Item label="Người nhận">
                  {[document.receiverName, document.receiverPhone].filter(Boolean).join(' - ')}
                </Descriptions.Item>
                <Descriptions.Item label="Khách nhận ngày">
                  {formatDate(document.receivedDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Người ký nhận">{document.receivedBy}</Descriptions.Item>
                <Descriptions.Item label="Số biên bản">{document.signedDocumentRef}</Descriptions.Item>
                {document.sourceReturnId && (
                  <Descriptions.Item label="Đổi hàng theo">
                    Phiếu trả hàng #{document.sourceReturnId}
                  </Descriptions.Item>
                )}
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
              </Descriptions>
              <Table<SalesDeliveryLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1000 }}
              />
            </>
          )}
        </SalesDocumentDrawer>
      )}
      <FormModal<DeliveryForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập phiếu giao hàng' : 'Sửa phiếu giao hàng'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <DeliveryFields form={form} isNew={editing === 'new'} />}
      </FormModal>
      {confirming && (
        <ConfirmModal
          delivery={confirming}
          onClose={() => setConfirming(null)}
          onDone={() => {
            message.success('Đã ghi nhận khách nhận hàng.');
            void queryClient.invalidateQueries({ queryKey: ['sales-delivery', confirming.id] });
            void queryClient.invalidateQueries({ queryKey: ['sales-deliveries'] });
            setConfirming(null);
          }}
        />
      )}
    </Card>
  );
}

function DeliveryFields({ form, isNew }: { form: FormInstance<DeliveryForm>; isNew: boolean }) {
  const companyId = useCompanyId();
  const units = useCatalog('units');
  const orderId = Form.useWatch('orderId', form) as number | undefined;
  const orders = useQuery({
    queryKey: ['sales-orders', 'open'],
    queryFn: () => salesOrderApi.list({ page: 0, size: 100, companyId, openOnly: true }),
  });
  const order = useQuery({
    queryKey: ['sales-order', orderId],
    queryFn: () => salesOrderApi.get(orderId as number),
    enabled: typeof orderId === 'number',
  }).data;
  const goods = (order?.lines ?? []).filter((line) => !line.serviceLine);
  const fillFromOrder = (picked: SalesOrder) => {
    form.setFieldValue(
      'lines',
      picked.lines
        .filter((line) => !line.serviceLine && (line.outstandingQuantity ?? 0) > 0)
        .map((line) => ({
          orderLineId: line.id,
          quantity: Math.floor(((line.outstandingQuantity ?? 0) / line.conversionFactor) * 1000) / 1000,
        })),
    );
  };
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={8}>
          <Form.Item
            name="orderId"
            label="Đơn bán (đã duyệt)"
            rules={[{ required: true, message: 'Chọn đơn' }]}
          >
            <Select
              disabled={!isNew}
              showSearch
              optionFilterProp="label"
              loading={orders.isLoading}
              options={(orders.data?.items ?? []).map((item) => ({
                value: item.id,
                label: `${item.documentNo} · ${item.customerName ?? ''}`,
              }))}
              onChange={async (value: number) => fillFromOrder(await salesOrderApi.get(value))}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name="documentDate"
            label="Ngày phiếu"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="postingDate" label="Ngày xuất kho">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="shipperName" label="Vận chuyển">
            <Input maxLength={200} />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="deliveryAddress" label="Địa chỉ giao" extra="Trống: theo đơn">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={12} md={6}>
          <Form.Item name="receiverName" label="Người nhận">
            <Input maxLength={200} />
          </Form.Item>
        </Col>
        <Col xs={12} md={6}>
          <Form.Item name="receiverPhone" label="Điện thoại">
            <Input maxLength={50} />
          </Form.Item>
        </Col>
        <Col xs={24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { remove }) => (
          <>
            {fields.map((field) => {
              const lineId = form.getFieldValue(['lines', field.name, 'orderLineId']) as number | undefined;
              const orderLine = goods.find((line) => line.id === lineId);
              return (
                <Card size="small" key={field.key} style={{ marginBottom: 8 }}>
                  <Row gutter={8}>
                    <Col xs={24} md={7}>
                      <Form.Item
                        name={[field.name, 'orderLineId']}
                        label="Dòng đơn"
                        rules={[{ required: true, message: 'Chọn dòng' }]}
                      >
                        <Select
                          options={goods.map((line) => ({
                            value: line.id,
                            label: `${line.lineNo}. ${line.productCode ?? ''} - ${line.productName ?? ''}`,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={3}>
                      <Form.Item
                        name={[field.name, 'quantity']}
                        label={`Số lượng${orderLine ? ` (${units.nameOf(orderLine.unitId)})` : ''}`}
                        rules={[{ required: true, message: 'Nhập SL' }]}
                      >
                        <InputNumber min={0.001} precision={3} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={3}>
                      <Form.Item name={[field.name, 'lotNo']} label="Lô" extra="Trống: FEFO">
                        <Input maxLength={50} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={5}>
                      <Form.Item name={[field.name, 'lotOverrideReason']} label="Lý do chọn lô khác FEFO">
                        <Input maxLength={500} />
                      </Form.Item>
                    </Col>
                    <Col xs={22} md={5}>
                      <Form.Item name={[field.name, 'serialNumbers']} label="Serial">
                        <Select mode="tags" tokenSeparators={[',', ' ']} />
                      </Form.Item>
                    </Col>
                    <Col xs={2} md={1}>
                      <Button style={{ marginTop: 30 }} danger onClick={() => remove(field.name)}>
                        x
                      </Button>
                    </Col>
                  </Row>
                </Card>
              );
            })}
          </>
        )}
      </Form.List>
    </>
  );
}

/** Khách nhận hàng: ngày nhận, người ký nhận, số biên bản (BR-SAL-22). */
function ConfirmModal({
  delivery,
  onClose,
  onDone,
}: {
  delivery: SalesDelivery;
  onClose(): void;
  onDone(): void;
}) {
  const [form] = Form.useForm<{
    receivedDate: Dayjs;
    receivedBy?: string;
    signedDocumentRef?: string;
    note?: string;
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
      await salesDeliveryApi.act(
        delivery.id,
        'confirm',
        { ...values, receivedDate: values.receivedDate.format('YYYY-MM-DD') },
        key,
      );
      onDone();
    } catch (err) {
      setError(applyServerErrors(form, err, ['receivedDate', 'receivedBy', 'signedDocumentRef', 'note']));
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <Modal
      open
      title={`Khách nhận hàng - ${delivery.documentNo}`}
      okText="Xác nhận"
      okButtonProps={{ loading: submitting }}
      onOk={submit}
      onCancel={onClose}
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Form form={form} layout="vertical" initialValues={{ receivedDate: dayjs() }}>
        <Form.Item
          name="receivedDate"
          label="Ngày khách nhận"
          rules={[{ required: true, message: 'Chọn ngày' }]}
        >
          <DatePicker format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item name="receivedBy" label="Người ký nhận">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="signedDocumentRef" label="Số biên bản giao nhận đã ký">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú">
          <Input maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** Biên bản giao nhận (BR-SAL-22): hàng, số lượng, lô, hạn dùng, serial, số lưu hành, xuất xứ; chỗ ký hai bên. */
function DeliveryNote({
  delivery,
  unitName,
}: {
  delivery: SalesDelivery;
  unitName: (id: number | null) => string;
}) {
  const cell = { border: '1px solid #999', padding: 4 } as const;
  return (
    <div>
      <Typography.Title level={4} style={{ textAlign: 'center' }}>
        BIÊN BẢN GIAO NHẬN HÀNG HÓA
      </Typography.Title>
      <p>
        Số: {delivery.documentNo} · Ngày xuất kho: {formatDate(delivery.postingDate)} · Theo đơn:{' '}
        {delivery.orderNo}
      </p>
      <p>
        Bên nhận: <strong>{delivery.customerName}</strong> · Địa chỉ: {delivery.deliveryAddress}
      </p>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {['#', 'Hàng hóa', 'ĐVT', 'Số lượng', 'Lô / hạn dùng / serial', 'Số lưu hành', 'Xuất xứ'].map(
              (head) => (
                <th key={head} style={cell}>
                  {head}
                </th>
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {delivery.lines.map((line) => (
            <tr key={line.id}>
              <td style={cell}>{line.lineNo}</td>
              <td style={cell}>
                {line.productCode} - {line.productName}
              </td>
              <td style={cell}>{unitName(line.unitId)}</td>
              <td style={{ ...cell, textAlign: 'right' }}>{formatNumber(line.quantity)}</td>
              <td style={cell}>
                {line.items.map((item, index) => (
                  <div key={index}>
                    {item.lotNo ?? ''} {item.expiryDate ? `HD ${formatDate(item.expiryDate)}` : ''}{' '}
                    {item.serialNo ? `#${item.serialNo}` : ''} ({formatNumber(item.baseQuantity)}){' '}
                    <QualityTag status={item.qualityStatus} />
                  </div>
                ))}
              </td>
              <td style={cell}>{line.registrationNo}</td>
              <td style={cell}>{line.originCountryCode}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Row style={{ marginTop: 32, textAlign: 'center' }}>
        <Col span={12}>
          <strong>Đại diện bên giao</strong>
          <p>(Ký, ghi rõ họ tên)</p>
        </Col>
        <Col span={12}>
          <strong>Đại diện bên nhận</strong>
          <p>(Ký, ghi rõ họ tên)</p>
        </Col>
      </Row>
    </div>
  );
}
