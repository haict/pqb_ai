import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Card,
  Checkbox,
  DatePicker,
  Descriptions,
  Empty,
  Input,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { debtApi } from '../api';
import { DEBT_ENTRY_TYPES, DEBT_ITEM_STATUSES, DEBT_PARTNER_ROLE, DEBT_TYPES } from '../labels';
import type { AgingRow, DebtEntryType, DebtItem, DebtItemStatus, DebtLedgerLine, DebtType } from '../types';

const money = (value: number) => formatMoney(value);

function ItemsTab({ debtType }: { debtType: DebtType }) {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [partnerId, setPartnerId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [openOnly, setOpenOnly] = useState(true);
  const [overdueOnly, setOverdueOnly] = useState(false);
  const { tableProps } = useServerTable(`debt-items-${debtType}`, debtApi.items, {
    companyId,
    debtType,
    partnerId,
    keyword,
    openOnly,
    overdueOnly,
  });
  const columns: TableColumnsType<DebtItem> = [
    { title: 'Đối tượng', render: (_, item) => `${item.partnerCode ?? ''} - ${item.partnerName ?? ''}` },
    { title: 'Số hóa đơn', dataIndex: 'invoiceNo', width: 140 },
    { title: 'Chứng từ nguồn', dataIndex: 'sourceNo', width: 190 },
    {
      title: 'Chi nhánh',
      dataIndex: 'branchId',
      width: 140,
      render: (value: number) => branches.nameOf(value),
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    { title: 'Hạn', dataIndex: 'dueDate', width: 110, sorter: true, render: formatDate },
    { title: 'Số gốc', dataIndex: 'originalAmount', width: 140, align: 'right', sorter: true, render: money },
    { title: 'Đã thanh toán', dataIndex: 'settledAmount', width: 140, align: 'right', render: money },
    { title: 'Còn lại', dataIndex: 'remainingAmount', width: 140, align: 'right', render: money },
    {
      title: 'Quá hạn',
      dataIndex: 'overdueDays',
      width: 100,
      align: 'right',
      render: (days: number) =>
        days > 0 ? <Typography.Text type="danger">{days} ngày</Typography.Text> : '',
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 170,
      render: (value: DebtItemStatus) => (
        <Tag color={DEBT_ITEM_STATUSES[value].color}>{DEBT_ITEM_STATUSES[value].label}</Tag>
      ),
    },
  ];
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <PartnerSelect
          allowClear
          role={DEBT_PARTNER_ROLE[debtType]}
          style={{ width: 280 }}
          value={partnerId}
          onChange={(value) => setPartnerId(value ?? undefined)}
        />
        <Input.Search
          placeholder="Số hóa đơn, số chứng từ"
          allowClear
          onSearch={setKeyword}
          style={{ width: 240 }}
        />
        <Checkbox checked={openOnly} onChange={(event) => setOpenOnly(event.target.checked)}>
          Chỉ khoản còn nợ
        </Checkbox>
        <Checkbox checked={overdueOnly} onChange={(event) => setOverdueOnly(event.target.checked)}>
          Chỉ khoản quá hạn
        </Checkbox>
      </Space>
      <Table<DebtItem> {...tableProps} size="small" columns={columns} scroll={{ x: 1500 }} />
    </>
  );
}

function AgingTab({ debtType }: { debtType: DebtType }) {
  const companyId = useCompanyId();
  const [asOf, setAsOf] = useState<Dayjs>(dayjs());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const query = useQuery({
    queryKey: ['debt-aging', debtType, companyId, asOf.format('YYYY-MM-DD'), page, size],
    queryFn: () => debtApi.aging({ companyId, debtType, asOf: asOf.format('YYYY-MM-DD'), page, size }),
    placeholderData: keepPreviousData,
  });
  const [m1 = 30, m2 = 60, m3 = 90] = query.data?.bucketDays ?? [];
  const columns: TableColumnsType<AgingRow> = [
    {
      title: 'Đối tượng',
      render: (_, row) => (row.partnerId ? `${row.partnerCode} - ${row.partnerName}` : 'Tổng'),
    },
    { title: 'Chưa đến hạn', dataIndex: 'notDue', width: 140, align: 'right', render: money },
    { title: `Quá hạn 1-${m1} ngày`, dataIndex: 'bucket1', width: 150, align: 'right', render: money },
    { title: `${m1 + 1}-${m2} ngày`, dataIndex: 'bucket2', width: 140, align: 'right', render: money },
    { title: `${m2 + 1}-${m3} ngày`, dataIndex: 'bucket3', width: 140, align: 'right', render: money },
    { title: `Trên ${m3} ngày`, dataIndex: 'bucket4', width: 140, align: 'right', render: money },
    { title: 'Tổng còn nợ', dataIndex: 'total', width: 150, align: 'right', render: money },
  ];
  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <span>Tại ngày</span>
        <DatePicker
          format="DD/MM/YYYY"
          allowClear={false}
          value={asOf}
          onChange={(value) => {
            setAsOf(value ?? dayjs());
            setPage(0);
          }}
        />
      </Space>
      <Table<AgingRow>
        rowKey={(row) => row.partnerId ?? 0}
        size="small"
        loading={query.isFetching}
        columns={columns}
        dataSource={query.data?.rows.items ?? []}
        scroll={{ x: 1100 }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: query.data?.rows.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          onChange: (next, nextSize) => {
            setPage(next - 1);
            setSize(nextSize);
          },
        }}
        summary={() =>
          query.data && (
            <Table.Summary.Row>
              {columns.map((column, index) => (
                <Table.Summary.Cell key={index} index={index} align={index === 0 ? 'left' : 'right'}>
                  <strong>
                    {index === 0
                      ? 'Tổng'
                      : money(
                          query.data.totals[(column as { dataIndex: keyof AgingRow }).dataIndex] as number,
                        )}
                  </strong>
                </Table.Summary.Cell>
              ))}
            </Table.Summary.Row>
          )
        }
      />
    </>
  );
}

function LedgerTab({ debtType }: { debtType: DebtType }) {
  const companyId = useCompanyId();
  const [partnerId, setPartnerId] = useState<number>();
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('year'), dayjs()]);
  const query = useQuery({
    queryKey: ['debt-ledger', debtType, companyId, partnerId, range[0].format(), range[1].format()],
    queryFn: () =>
      debtApi.ledger({
        companyId,
        debtType,
        partnerId: partnerId as number,
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
      }),
    enabled: partnerId !== undefined,
  });
  const ledger = query.data;
  const columns: TableColumnsType<DebtLedgerLine> = [
    { title: 'Ngày', dataIndex: 'postingDate', width: 110, render: formatDate },
    { title: 'Chứng từ', dataIndex: 'referenceNo', width: 190 },
    {
      title: 'Loại',
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
      render: (v: number) => (v ? money(v) : ''),
    },
    {
      title: 'Có',
      dataIndex: 'creditAmount',
      width: 140,
      align: 'right',
      render: (v: number) => (v ? money(v) : ''),
    },
    { title: 'Số dư', dataIndex: 'balance', width: 150, align: 'right', render: money },
  ];
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <PartnerSelect
          role={DEBT_PARTNER_ROLE[debtType]}
          style={{ width: 300 }}
          value={partnerId}
          onChange={(value) => setPartnerId(value ?? undefined)}
        />
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          allowClear={false}
          value={range}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
      </Space>
      {!ledger && <Empty description="Chọn đối tượng để xem sổ chi tiết công nợ" />}
      {ledger && (
        <>
          <Descriptions size="small" column={{ xs: 1, md: 4 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Số dư đầu kỳ">{money(ledger.openingBalance)}</Descriptions.Item>
            <Descriptions.Item label="Phát sinh Nợ">{money(ledger.totalDebit)}</Descriptions.Item>
            <Descriptions.Item label="Phát sinh Có">{money(ledger.totalCredit)}</Descriptions.Item>
            <Descriptions.Item label="Số dư cuối kỳ">{money(ledger.closingBalance)}</Descriptions.Item>
          </Descriptions>
          <Table<DebtLedgerLine>
            rowKey="id"
            size="small"
            loading={query.isFetching}
            pagination={false}
            columns={columns}
            dataSource={ledger.lines}
            scroll={{ x: 1000 }}
          />
        </>
      )}
    </>
  );
}

/**
 * Công nợ phải thu / phải trả (chỉ xem): khoản công nợ theo hóa đơn, tuổi nợ (BR-AR-05), sổ chi tiết theo đối tượng.
 * Số dư chỉ đổi qua ghi sổ chứng từ.
 */
export function DebtsPage({ debtType }: { debtType: DebtType }) {
  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Công nợ {DEBT_TYPES[debtType].toLowerCase()}
      </Typography.Title>
      <Tabs
        destroyOnHidden
        items={[
          { key: 'items', label: 'Theo hóa đơn', children: <ItemsTab debtType={debtType} /> },
          { key: 'aging', label: 'Tuổi nợ', children: <AgingTab debtType={debtType} /> },
          { key: 'ledger', label: 'Sổ chi tiết', children: <LedgerTab debtType={debtType} /> },
        ]}
      />
    </Card>
  );
}
