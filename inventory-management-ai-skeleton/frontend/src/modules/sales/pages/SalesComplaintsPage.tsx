import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
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
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { applyServerErrors, validated } from '../../../shared/forms';
import { formatDate, formatNumber } from '../../inventory/labels';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { salesComplaintApi } from '../api';
import { COMPLAINT_STATUSES, DECISIONS } from '../labels';
import type { ComplaintDecision, ComplaintStatus, SalesComplaint } from '../types';
import { DecimalInput } from '../../../shared/components/DecimalInput';

interface ComplaintForm {
  branchId: number;
  customerId?: number;
  receivedDate: Dayjs;
  productId?: number;
  lotNo?: string;
  serialNo?: string;
  quantity?: number;
  description?: string;
}

const FIELDS = [
  'branchId',
  'customerId',
  'receivedDate',
  'productId',
  'lotNo',
  'serialNo',
  'quantity',
  'description',
];

type Step = 'inspect' | 'resolve' | 'cancel';

/**
 * Tiếp nhận hàng lỗi khách báo (BR-SAL-34): tiếp nhận, kiểm tra, quyết định đổi / sửa / trả / bảo hành / từ chối (đổi,
 * trả kèm phiếu trả hàng bán), hủy.
 */
export function SalesComplaintsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ComplaintStatus>();
  // Mở thẳng phiếu từ liên kết ?id= (truy ngược từ báo cáo hàng lỗi theo NCC, BR-RPT-11).
  const [searchParams] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(() => Number(searchParams.get('id')) || null);
  const [creating, setCreating] = useState(false);
  const [step, setStep] = useState<Step | null>(null);
  const { tableProps } = useServerTable('sales-complaints', salesComplaintApi.list, {
    companyId,
    keyword,
    status,
  });
  const detail = useQuery({
    queryKey: ['sales-complaint', selected],
    queryFn: () => salesComplaintApi.get(selected as number),
    enabled: selected !== null,
  }).data;

  const save = async (values: ComplaintForm, key: string) => {
    const created = await salesComplaintApi.create(
      { ...values, receivedDate: values.receivedDate.format('YYYY-MM-DD') },
      key,
    );
    message.success(`Đã tiếp nhận ${created.complaintNo}.`);
    setSelected(created.id);
    await queryClient.invalidateQueries({ queryKey: ['sales-complaints'] });
  };

  const columns: TableColumnsType<SalesComplaint> = [
    {
      title: 'Số phiếu',
      dataIndex: 'complaintNo',
      width: 190,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày nhận', dataIndex: 'receivedDate', width: 110, render: formatDate },
    { title: 'Khách hàng', render: (_, row) => `${row.customerCode ?? ''} - ${row.customerName ?? ''}` },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    {
      title: 'Quyết định',
      dataIndex: 'decision',
      width: 120,
      render: (value: ComplaintDecision | null) => (value ? DECISIONS[value] : ''),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: ComplaintStatus) => (
        <Tag color={COMPLAINT_STATUSES[value].color}>{COMPLAINT_STATUSES[value].label}</Tag>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Hàng lỗi khách báo
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số phiếu, mô tả"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 170 }}
            value={status}
            onChange={setStatus}
            options={(Object.keys(COMPLAINT_STATUSES) as ComplaintStatus[]).map((value) => ({
              value,
              label: COMPLAINT_STATUSES[value].label,
            }))}
          />
        }
        createLabel="Tiếp nhận"
        onCreate={can('sales-complaint:create') ? () => setCreating(true) : undefined}
      />
      <Table<SalesComplaint> {...tableProps} size="small" columns={columns} scroll={{ x: 1000 }} />
      <Drawer
        open={selected !== null}
        width={800}
        title={detail ? `Phiếu ${detail.complaintNo}` : ''}
        onClose={() => setSelected(null)}
      >
        {detail && (
          <>
            <Space style={{ marginBottom: 12 }} wrap>
              <Tag color={COMPLAINT_STATUSES[detail.status].color}>
                {COMPLAINT_STATUSES[detail.status].label}
              </Tag>
              {detail.status === 'OPEN' && can('sales-complaint:resolve') && (
                <Button onClick={() => setStep('inspect')}>Ghi kết quả kiểm tra</Button>
              )}
              {detail.status === 'INSPECTED' && can('sales-complaint:resolve') && (
                <Button type="primary" onClick={() => setStep('resolve')}>
                  Quyết định xử lý
                </Button>
              )}
              {(detail.status === 'OPEN' || detail.status === 'INSPECTED') &&
                can('sales-complaint:create') && (
                  <Button danger onClick={() => setStep('cancel')}>
                    Hủy
                  </Button>
                )}
            </Space>
            <Descriptions size="small" column={{ xs: 1, md: 2 }} bordered>
              <Descriptions.Item label="Khách hàng">{`${detail.customerCode ?? ''} - ${
                detail.customerName ?? ''
              }`}</Descriptions.Item>
              <Descriptions.Item label="Ngày nhận">{formatDate(detail.receivedDate)}</Descriptions.Item>
              <Descriptions.Item label="Hàng hóa">{`${detail.productCode ?? ''} - ${
                detail.productName ?? ''
              }`}</Descriptions.Item>
              <Descriptions.Item label="Số lượng">{formatNumber(detail.quantity)}</Descriptions.Item>
              <Descriptions.Item label="Lô">{detail.lotNo}</Descriptions.Item>
              <Descriptions.Item label="Serial">{detail.serialNo}</Descriptions.Item>
              <Descriptions.Item label="Mô tả lỗi" span={2}>
                {detail.description}
              </Descriptions.Item>
              <Descriptions.Item label="Kết quả kiểm tra" span={2}>
                {detail.inspectionResult}
              </Descriptions.Item>
              <Descriptions.Item label="Quyết định">
                {detail.decision ? DECISIONS[detail.decision] : ''}
              </Descriptions.Item>
              <Descriptions.Item label="Phiếu trả hàng">
                {detail.salesReturnId ? `#${detail.salesReturnId}` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="Ghi chú xử lý" span={2}>
                {detail.resolutionNote}
              </Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Drawer>
      <FormModal<ComplaintForm>
        open={creating}
        title="Tiếp nhận hàng lỗi"
        width={800}
        okText="Lưu"
        initialValues={{ branchId: branches.items[0]?.id, receivedDate: dayjs(), quantity: 1 }}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setCreating(false)}
      >
        <Row gutter={12}>
          <Col xs={24} md={8}>
            <Form.Item
              name="branchId"
              label="Chi nhánh"
              rules={[{ required: true, message: 'Chọn chi nhánh' }]}
            >
              <Select options={branches.options} />
            </Form.Item>
          </Col>
          <Col xs={24} md={10}>
            <Form.Item
              name="customerId"
              label="Khách hàng"
              rules={[{ required: true, message: 'Chọn khách' }]}
            >
              <PartnerSelect role="CUSTOMER" />
            </Form.Item>
          </Col>
          <Col xs={24} md={6}>
            <Form.Item
              name="receivedDate"
              label="Ngày nhận"
              rules={[{ required: true, message: 'Chọn ngày' }]}
            >
              <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={10}>
            <Form.Item name="productId" label="Hàng hóa" rules={[{ required: true, message: 'Chọn hàng' }]}>
              <ProductSelect />
            </Form.Item>
          </Col>
          <Col xs={8} md={5}>
            <Form.Item name="lotNo" label="Lô">
              <Input maxLength={50} />
            </Form.Item>
          </Col>
          <Col xs={8} md={5}>
            <Form.Item name="serialNo" label="Serial">
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          <Col xs={8} md={4}>
            <Form.Item name="quantity" label="Số lượng" rules={[{ required: true, message: 'Nhập SL' }]}>
              <DecimalInput min={0.001} />
            </Form.Item>
          </Col>
          <Col xs={24}>
            <Form.Item
              name="description"
              label="Mô tả lỗi"
              rules={[{ required: true, whitespace: true, message: 'Mô tả lỗi' }]}
            >
              <Input.TextArea rows={3} maxLength={1000} />
            </Form.Item>
          </Col>
        </Row>
      </FormModal>
      {step && detail && (
        <StepModal
          step={step}
          complaint={detail}
          onClose={() => setStep(null)}
          onDone={() => {
            setStep(null);
            message.success('Đã cập nhật phiếu.');
            void queryClient.invalidateQueries({ queryKey: ['sales-complaint', detail.id] });
            void queryClient.invalidateQueries({ queryKey: ['sales-complaints'] });
          }}
        />
      )}
    </Card>
  );
}

function StepModal({
  step,
  complaint,
  onClose,
  onDone,
}: {
  step: Step;
  complaint: SalesComplaint;
  onClose(): void;
  onDone(): void;
}) {
  const [form] = Form.useForm<{
    result?: string;
    decision?: ComplaintDecision;
    note?: string;
    salesReturnId?: number;
    reason?: string;
  }>();
  const [key] = useState(newIdempotencyKey);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submit = async () => {
    const values = await validated(form);
    if (!values) {
      return;
    }
    setSubmitting(true);
    try {
      await salesComplaintApi.act(complaint.id, step, values, key);
      onDone();
    } catch (err) {
      setError(applyServerErrors(form, err, ['result', 'decision', 'note', 'salesReturnId', 'reason']));
    } finally {
      setSubmitting(false);
    }
  };
  const titles: Record<Step, string> = {
    inspect: 'Kết quả kiểm tra',
    resolve: 'Quyết định xử lý',
    cancel: 'Hủy phiếu',
  };
  return (
    <Modal open title={titles[step]} okButtonProps={{ loading: submitting }} onOk={submit} onCancel={onClose}>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Form form={form} layout="vertical">
        {step === 'inspect' && (
          <Form.Item
            name="result"
            label="Kết quả"
            rules={[{ required: true, whitespace: true, message: 'Nhập kết quả' }]}
          >
            <Input.TextArea rows={3} maxLength={1000} />
          </Form.Item>
        )}
        {step === 'resolve' && (
          <>
            <Form.Item
              name="decision"
              label="Quyết định"
              rules={[{ required: true, message: 'Chọn quyết định' }]}
            >
              <Select
                options={(Object.keys(DECISIONS) as ComplaintDecision[]).map((value) => ({
                  value,
                  label: DECISIONS[value],
                }))}
              />
            </Form.Item>
            <Form.Item name="salesReturnId" label="Mã phiếu trả hàng bán (đổi / trả)">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú">
              <Input.TextArea rows={3} maxLength={1000} />
            </Form.Item>
          </>
        )}
        {step === 'cancel' && (
          <Form.Item
            name="reason"
            label="Lý do"
            rules={[{ required: true, whitespace: true, message: 'Nhập lý do' }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
