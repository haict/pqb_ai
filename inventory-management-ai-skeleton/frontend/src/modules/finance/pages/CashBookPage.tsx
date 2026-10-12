import { useQuery } from '@tanstack/react-query';
import {
  Card,
  DatePicker,
  Descriptions,
  Empty,
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
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { useDocumentTypes, useMyBranches } from '../../system/lookups';
import { cashBookApi } from '../api';
import { CASH_ACCOUNT_TYPES, CASH_PURPOSES } from '../labels';
import { useCashAccounts } from '../lookups';
import type { CashAccountType, CashBookLine, CashFlowAccountRow, CashPurpose } from '../types';

type Range = [Dayjs, Dayjs];

const DEFAULT_RANGE: Range = [dayjs().startOf('month'), dayjs()];

function CashBookTab() {
  const cashAccounts = useCashAccounts();
  const documentTypes = useDocumentTypes();
  const [cashAccountId, setCashAccountId] = useState<number>();
  const [range, setRange] = useState<Range>(DEFAULT_RANGE);
  const query = useQuery({
    queryKey: ['cash-book', cashAccountId, range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD')],
    queryFn: () =>
      cashBookApi.book({
        cashAccountId: cashAccountId as number,
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
      }),
    enabled: cashAccountId !== undefined,
  });
  const book = query.data;
  const columns: TableColumnsType<CashBookLine> = [
    { title: 'Ngày', dataIndex: 'postingDate', width: 110, render: formatDate },
    {
      title: 'Chứng từ',
      width: 220,
      render: (_, line) => `${documentTypes.nameOf(line.referenceType)} ${line.referenceNo}`,
    },
    {
      title: 'Nội dung',
      dataIndex: 'transactionCode',
      width: 170,
      render: (value: CashPurpose, line) => (
        <Space size={4}>
          {CASH_PURPOSES[value] ?? value}
          {line.reversal && <Tag>Đảo</Tag>}
        </Space>
      ),
    },
    { title: 'Đối tượng', width: 200, render: (_, line) => line.partnerName ?? line.counterpartyName },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Thu',
      dataIndex: 'inAmount',
      width: 140,
      align: 'right',
      render: (value: number) => (value ? formatMoney(value) : ''),
    },
    {
      title: 'Chi',
      dataIndex: 'outAmount',
      width: 140,
      align: 'right',
      render: (value: number) => (value ? formatMoney(value) : ''),
    },
    { title: 'Tồn', dataIndex: 'balance', width: 150, align: 'right', render: formatMoney },
  ];
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="Chọn quỹ / tài khoản"
          style={{ width: 280 }}
          value={cashAccountId}
          onChange={setCashAccountId}
          options={cashAccounts.options}
        />
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          allowClear={false}
          value={range}
          onChange={(value) => value?.[0] && value[1] && setRange([value[0], value[1]])}
        />
      </Space>
      {!book ? (
        <Empty description="Chọn quỹ để xem sổ" />
      ) : (
        <>
          <Descriptions size="small" column={{ xs: 1, md: 4 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Tồn đầu kỳ">{formatMoney(book.openingBalance)}</Descriptions.Item>
            <Descriptions.Item label="Tổng thu">{formatMoney(book.totalIn)}</Descriptions.Item>
            <Descriptions.Item label="Tổng chi">{formatMoney(book.totalOut)}</Descriptions.Item>
            <Descriptions.Item label="Tồn cuối kỳ">{formatMoney(book.closingBalance)}</Descriptions.Item>
          </Descriptions>
          <Table<CashBookLine>
            rowKey="id"
            size="small"
            loading={query.isFetching}
            columns={columns}
            dataSource={book.lines}
            pagination={{ pageSize: 50, showSizeChanger: false }}
            scroll={{ x: 1300 }}
          />
        </>
      )}
    </>
  );
}

function CashFlowTab() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [branchId, setBranchId] = useState<number>();
  const [range, setRange] = useState<Range>(DEFAULT_RANGE);
  const query = useQuery({
    queryKey: [
      'cash-flow',
      companyId,
      branchId,
      range[0].format('YYYY-MM-DD'),
      range[1].format('YYYY-MM-DD'),
    ],
    queryFn: () =>
      cashBookApi.flow({
        companyId,
        branchId,
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
      }),
    enabled: companyId > 0,
  });
  const flow = query.data;
  const accountColumns: TableColumnsType<CashFlowAccountRow> = [
    { title: 'Quỹ', render: (_, row) => `${row.code} - ${row.name}` },
    {
      title: 'Loại',
      dataIndex: 'accountType',
      width: 110,
      render: (value: CashAccountType) => CASH_ACCOUNT_TYPES[value],
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'branchId',
      width: 160,
      render: (value: number) => branches.nameOf(value),
    },
    { title: 'Đầu kỳ', dataIndex: 'openingBalance', width: 150, align: 'right', render: formatMoney },
    { title: 'Thu', dataIndex: 'inAmount', width: 150, align: 'right', render: formatMoney },
    { title: 'Chi', dataIndex: 'outAmount', width: 150, align: 'right', render: formatMoney },
    { title: 'Cuối kỳ', dataIndex: 'closingBalance', width: 150, align: 'right', render: formatMoney },
  ];
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="Mọi chi nhánh"
          style={{ width: 220 }}
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
      </Space>
      {flow && (
        <>
          <Descriptions size="small" column={{ xs: 1, md: 4 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Đầu kỳ">{formatMoney(flow.openingBalance)}</Descriptions.Item>
            <Descriptions.Item label="Tổng thu">{formatMoney(flow.totalIn)}</Descriptions.Item>
            <Descriptions.Item label="Tổng chi">{formatMoney(flow.totalOut)}</Descriptions.Item>
            <Descriptions.Item label="Cuối kỳ">{formatMoney(flow.closingBalance)}</Descriptions.Item>
          </Descriptions>
          <Table<CashFlowAccountRow>
            rowKey="cashAccountId"
            size="small"
            loading={query.isFetching}
            columns={accountColumns}
            dataSource={flow.accounts}
            pagination={false}
            scroll={{ x: 1000 }}
          />
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            Theo nội dung thu chi
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            Chuyển tiền nội bộ có ở cả thu và chi; tổng toàn công ty triệt tiêu.
          </Typography.Paragraph>
          <Table
            rowKey="transactionCode"
            size="small"
            pagination={false}
            dataSource={flow.purposes}
            columns={[
              {
                title: 'Nội dung',
                dataIndex: 'transactionCode',
                render: (value: CashPurpose) => CASH_PURPOSES[value] ?? value,
              },
              { title: 'Thu', dataIndex: 'inAmount', width: 160, align: 'right', render: formatMoney },
              { title: 'Chi', dataIndex: 'outAmount', width: 160, align: 'right', render: formatMoney },
            ]}
          />
        </>
      )}
    </>
  );
}

/**
 * Sổ quỹ, sổ tiền gửi và dòng tiền thực tế (BR-CSH-01, BR-CSH-06 phần thực tế). Số liệu tính từ sổ quỹ; mỗi dòng có
 * chứng từ nguồn để truy ngược. Dự kiến dòng tiền làm ở Giai đoạn 10.
 */
export function CashBookPage() {
  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Sổ quỹ, dòng tiền
      </Typography.Title>
      <Tabs
        items={[
          { key: 'book', label: 'Sổ quỹ / sổ tiền gửi', children: <CashBookTab /> },
          { key: 'flow', label: 'Dòng tiền thực tế', children: <CashFlowTab /> },
        ]}
      />
    </Card>
  );
}
