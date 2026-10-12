import { Card, DatePicker, Drawer, Select, Table, Typography, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { formatDate } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { useDocumentTypes } from '../../system/lookups';
import { journalApi } from '../api';
import { JournalEntryView, JournalStatusTag } from '../components/JournalEntryView';
import { JOURNAL_STATUSES, formatMoney } from '../labels';
import { usePostableAccounts } from '../lookups';
import type { JournalEntryStatus, JournalEntrySummary } from '../types';

function JournalDrawer({ id, onClose }: { id: number; onClose(): void }) {
  const query = useQuery({ queryKey: ['journal-entry', id], queryFn: () => journalApi.get(id) });
  return (
    <Drawer
      open
      width={960}
      title={query.data ? `Bút toán ${query.data.entry.entryNo}` : ''}
      onClose={onClose}
    >
      {query.data && <JournalEntryView journal={query.data} />}
    </Drawer>
  );
}

/**
 * Bút toán (chỉ xem, BR-ACC-03): sinh tự động khi chứng từ nguồn ghi sổ, không sửa tay; hủy bằng đảo chứng từ gốc.
 */
export function JournalEntriesPage() {
  const companyId = useCompanyId();
  const documentTypes = useDocumentTypes();
  const accounts = usePostableAccounts();
  const [keyword, setKeyword] = useState('');
  const [sourceType, setSourceType] = useState<string>();
  const [accountId, setAccountId] = useState<number>();
  const [status, setStatus] = useState<JournalEntryStatus>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const { tableProps } = useServerTable('journal-entries', journalApi.list, {
    companyId,
    keyword,
    sourceType,
    accountId,
    status,
    dateFrom: range?.[0]?.format('YYYY-MM-DD'),
    dateTo: range?.[1]?.format('YYYY-MM-DD'),
  });

  const columns: TableColumnsType<JournalEntrySummary> = [
    {
      title: 'Số bút toán',
      dataIndex: 'entryNo',
      width: 200,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    {
      title: 'Ngày hạch toán',
      dataIndex: 'postingDate',
      width: 140,
      sorter: true,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Chứng từ nguồn',
      width: 260,
      render: (_, row) => `${documentTypes.nameOf(row.sourceType)} ${row.sourceNo}`,
    },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      width: 160,
      align: 'right',
      render: (value: number) => formatMoney(value),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (value: JournalEntryStatus) => <JournalStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Bút toán
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số bút toán, số chứng từ"
        onSearch={setKeyword}
        extraFilters={
          <>
            <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(value) => setRange(value)} />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Loại chứng từ"
              style={{ width: 220 }}
              value={sourceType}
              onChange={setSourceType}
              options={documentTypes.options}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Tài khoản"
              style={{ width: 220 }}
              value={accountId}
              onChange={setAccountId}
              options={accounts.options}
            />
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 140 }}
              value={status}
              onChange={setStatus}
              options={Object.entries(JOURNAL_STATUSES).map(([value, { label }]) => ({ value, label }))}
            />
          </>
        }
      />
      <Table<JournalEntrySummary> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && <JournalDrawer id={selected} onClose={() => setSelected(null)} />}
    </Card>
  );
}
