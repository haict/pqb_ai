import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { validated } from '../../../shared/forms';
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useDocumentTypes, useMyBranches } from '../../system/lookups';
import { reconciliationApi } from '../api';
import { moneyInputProps } from '../components/debtInputs';
import { DEBT_ENTRY_TYPES, DEBT_PARTNER_ROLE, DEBT_TYPES, RECONCILIATION_STATUSES } from '../labels';
import type {
  DebtEntryType,
  DebtItem,
  DebtLedgerLine,
  DebtReconciliation,
  DebtType,
  ReconciliationStatus,
} from '../types';

type Range = [Dayjs, Dayjs];

/** Bảng đối chiếu một đối tượng: chọn kỳ, xem / in, lập biên bản chốt số dư. */
function StatementTab() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const documentTypes = useDocumentTypes();
  const [debtType, setDebtType] = useState<DebtType>('RECEIVABLE');
  const [partnerId, setPartnerId] = useState<number>();
  const [branchId, setBranchId] = useState<number>();
  const [range, setRange] = useState<Range>([dayjs().startOf('year'), dayjs()]);
  const [saving, setSaving] = useState(false);
  const query = useQuery({
    queryKey: [
      'debt-statement',
      companyId,
      debtType,
      partnerId,
      branchId,
      range[0].format(),
      range[1].format(),
    ],
    queryFn: () =>
      reconciliationApi.statement({
        companyId,
        branchId,
        debtType,
        partnerId: partnerId as number,
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
      }),
    enabled: !!partnerId,
  });
  const statement = query.data;

  const createRecord = async () => {
    setSaving(true);
    try {
      const created = await reconciliationApi.create(
        {
          companyId,
          branchId: branchId ?? null,
          debtType,
          partnerId,
          periodFrom: range[0].format('YYYY-MM-DD'),
          asOfDate: range[1].format('YYYY-MM-DD'),
        },
        newIdempotencyKey(),
      );
      message.success(`Đã lập biên bản ${created.documentNo}, số dư ${formatMoney(created.systemBalance)}.`);
      await queryClient.invalidateQueries({ queryKey: ['debt-reconciliations'] });
    } finally {
      setSaving(false);
    }
  };

  const lineColumns: TableColumnsType<DebtLedgerLine> = [
    { title: 'Ngày', dataIndex: 'postingDate', width: 110, render: formatDate },
    {
      title: 'Chứng từ',
      width: 220,
      render: (_, line) => `${documentTypes.nameOf(line.referenceType)} ${line.referenceNo}`,
    },
    {
      title: 'Nội dung',
      dataIndex: 'entryType',
      width: 150,
      render: (value: DebtEntryType) => DEBT_ENTRY_TYPES[value],
    },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Nợ',
      dataIndex: 'debitAmount',
      width: 140,
      align: 'right',
      render: (v: number) => (v ? formatMoney(v) : ''),
    },
    {
      title: 'Có',
      dataIndex: 'creditAmount',
      width: 140,
      align: 'right',
      render: (v: number) => (v ? formatMoney(v) : ''),
    },
    { title: 'Số dư', dataIndex: 'balance', width: 150, align: 'right', render: formatMoney },
  ];
  const itemColumns: TableColumnsType<DebtItem> = [
    { title: 'Hóa đơn', render: (_, item) => item.invoiceNo ?? item.sourceNo },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, render: formatDate },
    { title: 'Hạn', dataIndex: 'dueDate', width: 110, render: formatDate },
    { title: 'Gốc', dataIndex: 'originalAmount', width: 140, align: 'right', render: formatMoney },
    { title: 'Còn nợ', dataIndex: 'remainingAmount', width: 140, align: 'right', render: formatMoney },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 140 }}
          value={debtType}
          onChange={(value) => {
            setDebtType(value);
            setPartnerId(undefined);
          }}
          options={Object.entries(DEBT_TYPES).map(([value, label]) => ({ value, label }))}
        />
        <PartnerSelect
          style={{ width: 300 }}
          role={DEBT_PARTNER_ROLE[debtType]}
          value={partnerId}
          onChange={(value) => setPartnerId(value)}
        />
        <Select
          allowClear
          placeholder="Mọi chi nhánh"
          style={{ width: 200 }}
          value={branchId}
          onChange={setBranchId}
          options={branches.options}
        />
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          allowClear={false}
          value={range}
          onChange={(value) => value?.[0] && value[1] && setRange([value[0], value[1]])}
        />
        {statement && <Button onClick={() => window.print()}>In</Button>}
        {statement && can('debt-reconciliation:manage') && (
          <Button type="primary" loading={saving} onClick={() => void createRecord()}>
            Lập biên bản đối chiếu
          </Button>
        )}
      </Space>
      {!statement ? (
        <Empty description="Chọn đối tượng để lập bảng đối chiếu" />
      ) : (
        <>
          <Typography.Title level={5}>
            Đối chiếu công nợ {DEBT_TYPES[statement.debtType].toLowerCase()} với {statement.partnerCode} -{' '}
            {statement.partnerName} từ {formatDate(statement.dateFrom)} đến {formatDate(statement.dateTo)}
          </Typography.Title>
          <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Số dư đầu kỳ">
              {formatMoney(statement.openingBalance)}
            </Descriptions.Item>
            <Descriptions.Item label="Phát sinh Nợ">{formatMoney(statement.totalDebit)}</Descriptions.Item>
            <Descriptions.Item label="Phát sinh Có">{formatMoney(statement.totalCredit)}</Descriptions.Item>
            <Descriptions.Item label="Số dư cuối kỳ">
              {formatMoney(statement.closingBalance)}
            </Descriptions.Item>
            <Descriptions.Item label="Hóa đơn còn nợ">
              {formatMoney(statement.openItemsAmount)}
            </Descriptions.Item>
            <Descriptions.Item label="Trả trước chưa bù trừ">
              {formatMoney(statement.prepaymentAmount)}
            </Descriptions.Item>
          </Descriptions>
          <Table<DebtLedgerLine>
            rowKey="id"
            size="small"
            loading={query.isFetching}
            columns={lineColumns}
            dataSource={statement.lines}
            pagination={false}
            scroll={{ x: 1100 }}
          />
          {statement.openItems.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 16 }}>
                Hóa đơn còn nợ
              </Typography.Title>
              <Table<DebtItem>
                rowKey="id"
                size="small"
                columns={itemColumns}
                dataSource={statement.openItems}
                pagination={false}
              />
            </>
          )}
        </>
      )}
    </>
  );
}

interface ConfirmForm {
  confirmedBalance: number;
  contactName?: string;
  note?: string;
}

/** Danh sách biên bản: ghi nhận số đối tượng xác nhận, hủy. */
function RecordsTab() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const [status, setStatus] = useState<ReconciliationStatus>();
  const [confirming, setConfirming] = useState<{ item: DebtReconciliation; key: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<ConfirmForm>();
  const { modal } = App.useApp();
  const { tableProps } = useServerTable('debt-reconciliations', reconciliationApi.list, {
    companyId,
    status,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['debt-reconciliations'] });

  const confirm = async () => {
    if (!confirming) {
      return;
    }
    const values = await validated(form);
    if (!values) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await reconciliationApi.act(confirming.item.id, 'confirm', values, confirming.key);
      message.success('Đã ghi nhận xác nhận.');
      setConfirming(null);
      await refresh();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không ghi nhận được.');
    } finally {
      setSubmitting(false);
    }
  };

  const cancel = (item: DebtReconciliation) => {
    let reason = '';
    modal.confirm({
      title: `Hủy biên bản ${item.documentNo}`,
      content: (
        <Input.TextArea rows={2} maxLength={500} onChange={(event) => (reason = event.target.value)} />
      ),
      onOk: async () => {
        if (!reason.trim()) {
          message.error('Nhập lý do hủy.');
          throw new Error('reason');
        }
        await reconciliationApi.act(item.id, 'cancel', { reason }, newIdempotencyKey());
        await refresh();
      },
    });
  };

  const columns: TableColumnsType<DebtReconciliation> = [
    { title: 'Số biên bản', dataIndex: 'documentNo', width: 180 },
    { title: 'Ngày đối chiếu', dataIndex: 'asOfDate', width: 130, sorter: true, render: formatDate },
    { title: 'Loại', dataIndex: 'debtType', width: 100, render: (value: DebtType) => DEBT_TYPES[value] },
    { title: 'Đối tượng', render: (_, row) => `${row.partnerCode ?? ''} - ${row.partnerName ?? ''}` },
    { title: 'Số hệ thống', dataIndex: 'systemBalance', width: 140, align: 'right', render: formatMoney },
    { title: 'Số xác nhận', dataIndex: 'confirmedBalance', width: 140, align: 'right', render: formatMoney },
    { title: 'Chênh lệch', dataIndex: 'difference', width: 130, align: 'right', render: formatMoney },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: ReconciliationStatus) => (
        <Tag color={RECONCILIATION_STATUSES[value].color}>{RECONCILIATION_STATUSES[value].label}</Tag>
      ),
    },
    {
      title: 'Thao tác',
      width: 190,
      render: (_, row) =>
        row.status === 'PENDING' &&
        can('debt-reconciliation:manage') && (
          <Space>
            <Button
              size="small"
              onClick={() => {
                form.resetFields();
                setError(null);
                setConfirming({ item: row, key: newIdempotencyKey() });
              }}
            >
              Ghi nhận xác nhận
            </Button>
            <Button size="small" danger onClick={() => cancel(row)}>
              Hủy
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 160 }}
          value={status}
          onChange={setStatus}
          options={Object.entries(RECONCILIATION_STATUSES).map(([value, { label }]) => ({ value, label }))}
        />
      </Space>
      <Table<DebtReconciliation> {...tableProps} size="small" columns={columns} scroll={{ x: 1300 }} />
      <Modal
        open={confirming !== null}
        title={confirming ? `Xác nhận biên bản ${confirming.item.documentNo}` : ''}
        confirmLoading={submitting}
        onOk={() => void confirm()}
        onCancel={() => setConfirming(null)}
      >
        {confirming && (
          <Typography.Paragraph>
            Số dư hệ thống tại {formatDate(confirming.item.asOfDate)}:{' '}
            {formatMoney(confirming.item.systemBalance)}. Lệch thì lập chứng từ điều chỉnh công nợ.
          </Typography.Paragraph>
        )}
        {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
        <Form form={form} layout="vertical">
          <Form.Item
            name="confirmedBalance"
            label="Số dư đối tượng xác nhận"
            rules={[{ required: true, message: 'Nhập số dư' }]}
          >
            <InputNumber<number> {...moneyInputProps} />
          </Form.Item>
          <Form.Item name="contactName" label="Người xác nhận">
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

/**
 * Đối chiếu công nợ (BR-AR-06): bảng đối chiếu tính từ sổ chi tiết công nợ, in gửi đối tượng; biên bản chốt số dư và
 * ghi nhận số xác nhận. Gửi email chưa làm.
 */
export function DebtReconciliationsPage() {
  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Đối chiếu công nợ
      </Typography.Title>
      <Tabs
        items={[
          { key: 'statement', label: 'Bảng đối chiếu', children: <StatementTab /> },
          { key: 'records', label: 'Biên bản đối chiếu', children: <RecordsTab /> },
        ]}
      />
    </Card>
  );
}
