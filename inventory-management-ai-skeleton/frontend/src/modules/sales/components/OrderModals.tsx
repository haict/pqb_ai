import { Alert, Col, DatePicker, Form, Input, Modal, Row, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useIdempotencyKey } from '../../../shared/api/useIdempotencyKey';
import { applyServerErrors, validated } from '../../../shared/forms';
import { QualityTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber } from '../../inventory/labels';
import { salesOrderApi } from '../api';
import type { SalesOrder } from '../types';

interface ModalProps {
  order: SalesOrder;
  onClose(): void;
  onDone(): void;
}

/** Khách nhận hàng (BR-SAL-22): ngày nhận, người ký nhận, số biên bản giao nhận đã ký. */
export function ConfirmDeliveryModal({ order, onClose, onDone }: ModalProps) {
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
      await salesOrderApi.act(
        order.id,
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
      title={`Khách nhận hàng - ${order.documentNo}`}
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

/** Ghi số hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31); trùng số với đơn khác thì server từ chối. */
export function EinvoiceModal({ order, onClose, onDone }: ModalProps) {
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
      await salesOrderApi.act(
        order.id,
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
      title={`Số hóa đơn điện tử - ${order.documentNo}`}
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
          series: order.einvoiceSeries ?? undefined,
          number: order.einvoiceNo ?? undefined,
          date: order.einvoiceDate ? dayjs(order.einvoiceDate) : dayjs(order.postingDate),
          taxAuthorityCode: order.taxAuthorityCode ?? undefined,
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

/** Biên bản giao nhận (BR-SAL-22): hàng, số lượng, lô, hạn dùng, serial, số lưu hành, xuất xứ; chỗ ký hai bên. */
export function DeliveryNote({
  order,
  unitName,
}: {
  order: SalesOrder;
  unitName: (id: number | null) => string;
}) {
  const cell = { border: '1px solid #999', padding: 4 } as const;
  return (
    <div>
      <Typography.Title level={4} style={{ textAlign: 'center' }}>
        BIÊN BẢN GIAO NHẬN HÀNG HÓA
      </Typography.Title>
      <p>
        Theo đơn bán: {order.documentNo} · Ngày xuất kho: {formatDate(order.postingDate)} · Phiếu xuất:{' '}
        {order.inventoryDocumentNo}
      </p>
      <p>
        Bên nhận: <strong>{order.customerName}</strong> · Địa chỉ: {order.deliveryAddress} · Người nhận:{' '}
        {[order.receiverName, order.receiverPhone].filter(Boolean).join(' - ')}
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
          {order.lines
            .filter((line) => !line.serviceLine)
            .map((line) => (
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
