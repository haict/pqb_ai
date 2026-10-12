import { Descriptions, Table, Tag, type TableColumnsType } from 'antd';
import { formatDate } from '../../inventory/labels';
import { useMyBranches } from '../../system/lookups';
import { JOURNAL_KINDS, JOURNAL_STATUSES, formatMoney } from '../labels';
import type { JournalEntry, JournalEntryLine, JournalEntryStatus } from '../types';

export function JournalStatusTag({ status }: { status: JournalEntryStatus }) {
  const { label, color } = JOURNAL_STATUSES[status];
  return <Tag color={color}>{label}</Tag>;
}

const LINE_COLUMNS: TableColumnsType<JournalEntryLine> = [
  { title: '#', dataIndex: 'lineNo', width: 50 },
  {
    title: 'Tài khoản',
    dataIndex: 'accountCode',
    render: (code: string | null, line) => `${code ?? `#${line.accountId}`} - ${line.accountName ?? ''}`,
  },
  {
    title: 'Nợ',
    dataIndex: 'debitAmount',
    width: 150,
    align: 'right',
    render: (value: number) => (value > 0 ? formatMoney(value) : ''),
  },
  {
    title: 'Có',
    dataIndex: 'creditAmount',
    width: 150,
    align: 'right',
    render: (value: number) => (value > 0 ? formatMoney(value) : ''),
  },
  { title: 'Mã nghiệp vụ', dataIndex: 'transactionCode', width: 180 },
  {
    title: 'Đối tượng',
    dataIndex: 'partnerId',
    width: 110,
    render: (value: number | null) => (value ? `#${value}` : ''),
  },
  { title: 'Diễn giải', dataIndex: 'description' },
];

/** Thông tin chung và dòng Nợ / Có của một bút toán (dùng ở trang bút toán và tab bút toán của chứng từ). */
export function JournalEntryView({ journal }: { journal: JournalEntry }) {
  const branches = useMyBranches();
  const { entry } = journal;
  return (
    <>
      <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
        <Descriptions.Item label="Số bút toán">{entry.entryNo}</Descriptions.Item>
        <Descriptions.Item label="Trạng thái">
          <JournalStatusTag status={entry.status} />
        </Descriptions.Item>
        <Descriptions.Item label="Ngày hạch toán">{formatDate(entry.postingDate)}</Descriptions.Item>
        <Descriptions.Item label="Chi nhánh">{branches.nameOf(entry.branchId)}</Descriptions.Item>
        <Descriptions.Item label="Chứng từ nguồn">
          {entry.sourceType} {entry.sourceNo}
        </Descriptions.Item>
        <Descriptions.Item label="Loại">{JOURNAL_KINDS[entry.entryKind]}</Descriptions.Item>
        <Descriptions.Item label="Tổng tiền">{formatMoney(entry.totalAmount)}</Descriptions.Item>
        {entry.reversalOfId && (
          <Descriptions.Item label="Đảo của bút toán">#{entry.reversalOfId}</Descriptions.Item>
        )}
        {journal.reversedBy && (
          <Descriptions.Item label="Đã đảo bởi">{journal.reversedBy.entryNo}</Descriptions.Item>
        )}
        <Descriptions.Item label="Diễn giải" span={2}>
          {entry.description}
        </Descriptions.Item>
      </Descriptions>
      <Table<JournalEntryLine>
        rowKey="id"
        size="small"
        pagination={false}
        columns={LINE_COLUMNS}
        dataSource={journal.lines}
        scroll={{ x: 900 }}
      />
    </>
  );
}
