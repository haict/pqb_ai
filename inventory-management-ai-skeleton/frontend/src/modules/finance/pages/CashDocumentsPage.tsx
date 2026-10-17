import { EditOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { journalApi } from '../../accounting/api';
import { JournalEntryView } from '../../accounting/components/JournalEntryView';
import { formatMoney } from '../../accounting/labels';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { useMyBranches } from '../../system/lookups';
import { cashDocumentApi } from '../api';
import { CashDocumentFields, type CashDocumentForm } from '../components/CashDocumentFields';
import { ALLOCATION_MODES, CASH_DOCUMENT_TYPES, CASH_PURPOSES, DEBT_ITEM_STATUSES } from '../labels';
import { useCashAccounts } from '../lookups';
import type {
  CashDocument,
  CashDocumentLine,
  CashDocumentType,
  FinanceDocumentStatus,
  PaymentAllocation,
} from '../types';
import { BRANCH_VISIBLE } from '../../../shared/branch';

const STATUSES: FinanceDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

const FIELDS = [
  'branchId',
  'documentType',
  'purpose',
  'documentDate',
  'postingDate',
  'cashAccountId',
  'targetCashAccountId',
  'partnerId',
  'counterpartyName',
  'allocationMode',
  'amount',
  'description',
  'lines',
];

/** Thao tác hiện theo trạng thái và quyền; server vẫn kiểm tra lại (frontend.md mục 3). */
function cashActions(status: FinanceDocumentStatus, can: (permission: string) => boolean): string[] {
  const create = can('cash-document:create');
  const approve = can('cash-document:approve');
  const actions: Record<FinanceDocumentStatus, string[]> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(approve ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(can('cash-document:post') ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: can('cash-document:reverse') ? ['reverse'] : [],
    CANCELLED: [],
    REVERSED: [],
  };
  return actions[status];
}

const LINE_COLUMNS: TableColumnsType<CashDocumentLine> = [
  { title: '#', dataIndex: 'lineNo', width: 50 },
  {
    title: 'Hóa đơn / tài khoản',
    render: (_, line) =>
      line.debtItemId
        ? `Hóa đơn ${line.invoiceNo ?? ''}`
        : `${line.accountCode ?? ''} - ${line.accountName ?? ''}`,
  },
  { title: 'Khoản mục', dataIndex: 'costItemName', width: 180 },
  { title: 'Số tiền', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
  { title: 'Diễn giải', dataIndex: 'description' },
];

const ALLOCATION_COLUMNS: TableColumnsType<PaymentAllocation> = [
  { title: 'Hóa đơn', render: (_, row) => row.invoiceNo ?? row.debtSourceNo },
  { title: 'Hạn thanh toán', dataIndex: 'dueDate', width: 140, render: formatDate },
  { title: 'Số tiền', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
  {
    title: 'Trạng thái',
    dataIndex: 'status',
    width: 120,
    render: (value: PaymentAllocation['status']) =>
      value === 'ACTIVE' ? 'Đã phân bổ' : DEBT_ITEM_STATUSES.REVERSED.label,
  },
];

function CashDocumentDrawer({
  id,
  onEdit,
  onClose,
}: {
  id: number;
  onEdit(document: CashDocument): void;
  onClose(): void;
}) {
  const can = useCan();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const query = useQuery({ queryKey: ['cash-document', id], queryFn: () => cashDocumentApi.get(id) });
  const document = query.data;
  const posted = document && (document.status === 'POSTED' || document.status === 'REVERSED');
  const journals = useQuery({
    queryKey: ['journal-entries', 'by-source', document?.documentType, id, document?.status],
    queryFn: () => journalApi.bySource(companyId, document?.documentType ?? '', id),
    enabled: !!posted && can('journal:view'),
  });
  const editable = document && (document.status === 'DRAFT' || document.status === 'REJECTED');
  return (
    <Drawer
      open
      width={1000}
      title={document ? `${CASH_DOCUMENT_TYPES[document.documentType]} ${document.documentNo}` : ''}
      onClose={onClose}
    >
      {document && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={document.status} />
            <Space wrap>
              {editable && can('cash-document:create') && (
                <Button onClick={() => onEdit(document)} icon={<EditOutlined />}>
                  Sửa
                </Button>
              )}
              <DocumentActions
                actions={cashActions(document.status, can)}
                onAction={async (action, body, key) => {
                  await cashDocumentApi.act(id, action, body, key);
                  void queryClient.invalidateQueries({ queryKey: ['cash-document', id] });
                  void queryClient.invalidateQueries({ queryKey: ['cash-documents'] });
                  void queryClient.invalidateQueries({ queryKey: ['cash-accounts'] });
                }}
              />
            </Space>
          </Space>
          <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Nội dung">{CASH_PURPOSES[document.purpose]}</Descriptions.Item>
            {BRANCH_VISIBLE && (
              <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
            )}
            <Descriptions.Item label="Ngày chứng từ">{formatDate(document.documentDate)}</Descriptions.Item>
            <Descriptions.Item label="Ngày hạch toán">{formatDate(document.postingDate)}</Descriptions.Item>
            <Descriptions.Item label={document.targetCashAccountId ? 'Quỹ chuyển' : 'Quỹ / tài khoản'}>
              {`${document.cashAccountCode ?? ''} - ${document.cashAccountName ?? ''}`}
            </Descriptions.Item>
            {document.targetCashAccountId && (
              <Descriptions.Item label="Quỹ nhận">
                {`${document.targetCashAccountCode ?? ''} - ${document.targetCashAccountName ?? ''}`}
              </Descriptions.Item>
            )}
            {document.partnerId && (
              <Descriptions.Item label="Đối tượng">{`${document.partnerCode ?? ''} - ${document.partnerName ?? ''}`}</Descriptions.Item>
            )}
            {document.counterpartyName && (
              <Descriptions.Item label="Người nộp / nhận">{document.counterpartyName}</Descriptions.Item>
            )}
            <Descriptions.Item label="Số tiền">{formatMoney(document.totalAmount)}</Descriptions.Item>
            {document.allocationMode && (
              <Descriptions.Item label="Phân bổ">
                {ALLOCATION_MODES[document.allocationMode]}
              </Descriptions.Item>
            )}
            {posted && document.allocationMode && (
              <Descriptions.Item label="Đã phân bổ / trả trước">
                {`${formatMoney(document.allocatedAmount)} / ${formatMoney(document.prepaymentAmount)}`}
              </Descriptions.Item>
            )}
            {document.reverseDate && (
              <Descriptions.Item label="Ngày đảo">{formatDate(document.reverseDate)}</Descriptions.Item>
            )}
            {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
            <Descriptions.Item label="Diễn giải" span={2}>
              {document.description}
            </Descriptions.Item>
          </Descriptions>
          {document.lines.length > 0 && (
            <Table<CashDocumentLine>
              rowKey="id"
              size="small"
              pagination={false}
              columns={LINE_COLUMNS}
              dataSource={document.lines}
              scroll={{ x: 800 }}
            />
          )}
          {document.allocations.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 16 }}>
                Phân bổ vào hóa đơn
              </Typography.Title>
              <Table<PaymentAllocation>
                rowKey="id"
                size="small"
                pagination={false}
                columns={ALLOCATION_COLUMNS}
                dataSource={document.allocations}
              />
            </>
          )}
          {(journals.data ?? []).map((journal) => (
            <div key={journal.entry.id} style={{ marginTop: 16 }}>
              <Typography.Title level={5}>Bút toán {journal.entry.entryNo}</Typography.Title>
              <JournalEntryView journal={journal} />
            </div>
          ))}
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            Lịch sử duyệt
          </Typography.Title>
          <ApprovalHistory requests={document.approvals} />
        </>
      )}
    </Drawer>
  );
}

/**
 * Thu chi, ngân hàng (BR-CSH-02, 03, BR-AR-03): phiếu thu, phiếu chi, giấy báo có, ủy nhiệm chi, chuyển tiền nội bộ.
 * Chứng từ chi từ ngưỡng duyệt trở lên phải duyệt; ghi sổ cập nhật sổ quỹ, công nợ, bút toán cùng lúc.
 */
export function CashDocumentsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const cashAccounts = useCashAccounts();
  const [keyword, setKeyword] = useState('');
  const [documentType, setDocumentType] = useState<CashDocumentType>();
  const [status, setStatus] = useState<FinanceDocumentStatus>();
  const [cashAccountId, setCashAccountId] = useState<number>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<CashDocument | 'new' | null>(null);
  const { tableProps } = useServerTable('cash-documents', cashDocumentApi.list, {
    companyId,
    keyword,
    documentType,
    status,
    cashAccountId,
    dateFrom: range?.[0]?.format('YYYY-MM-DD'),
    dateTo: range?.[1]?.format('YYYY-MM-DD'),
  });

  const save = async (values: CashDocumentForm, key: string) => {
    const input = {
      branchId: values.branchId,
      documentType: values.documentType,
      purpose: values.purpose,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      cashAccountId: values.cashAccountId,
      targetCashAccountId: values.targetCashAccountId ?? null,
      partnerId: values.partnerId ?? null,
      counterpartyName: values.counterpartyName,
      allocationMode: values.allocationMode ?? null,
      amount: values.amount ?? null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        debtItemId: line.debtItemId ?? null,
        accountId: line.accountId ?? null,
        costItemId: line.costItemId ?? null,
        amount: line.amount,
        description: line.description,
      })),
    };
    if (editing === 'new') {
      const created = await cashDocumentApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await cashDocumentApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['cash-document', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['cash-documents'] });
  };

  const initialValues: Partial<CashDocumentForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          documentType: editing.documentType,
          purpose: editing.purpose,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          cashAccountId: editing.cashAccountId,
          targetCashAccountId: editing.targetCashAccountId ?? undefined,
          partnerId: editing.partnerId ?? undefined,
          partnerLabel: editing.partnerId
            ? `${editing.partnerCode ?? ''} - ${editing.partnerName ?? ''}`
            : undefined,
          counterpartyName: editing.counterpartyName ?? undefined,
          allocationMode: editing.allocationMode ?? undefined,
          amount: editing.allocationMode || editing.targetCashAccountId ? editing.totalAmount : undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            debtItemId: line.debtItemId ?? undefined,
            accountId: line.accountId ?? undefined,
            costItemId: line.costItemId ?? undefined,
            amount: line.amount,
            description: line.description ?? undefined,
          })),
        }
      : {
          branchId: branches.defaultId,
          documentType: 'PT',
          purpose: 'CUSTOMER_RECEIPT',
          allocationMode: 'AUTO',
          documentDate: dayjs(),
          lines: [],
        };

  const columns: TableColumnsType<CashDocument> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    {
      title: 'Loại',
      dataIndex: 'documentType',
      width: 150,
      render: (value: CashDocumentType) => CASH_DOCUMENT_TYPES[value],
    },
    {
      title: 'Nội dung',
      dataIndex: 'purpose',
      width: 180,
      render: (value: CashDocument['purpose']) => CASH_PURPOSES[value],
    },
    {
      title: 'Quỹ',
      width: 160,
      render: (_, row) =>
        row.targetCashAccountCode
          ? `${row.cashAccountCode} → ${row.targetCashAccountCode}`
          : row.cashAccountCode,
    },
    {
      title: 'Đối tượng',
      width: 220,
      render: (_, row) => (row.partnerName ? row.partnerName : row.counterpartyName),
    },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Số tiền',
      dataIndex: 'totalAmount',
      width: 150,
      align: 'right',
      sorter: true,
      render: formatMoney,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: FinanceDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Thu chi, ngân hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải, người nộp / nhận"
        onSearch={setKeyword}
        extraFilters={
          <>
            <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(value) => setRange(value)} />
            <Select
              allowClear
              placeholder="Loại chứng từ"
              style={{ width: 170 }}
              value={documentType}
              onChange={setDocumentType}
              options={Object.entries(CASH_DOCUMENT_TYPES).map(([value, label]) => ({ value, label }))}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Quỹ"
              style={{ width: 200 }}
              value={cashAccountId}
              onChange={setCashAccountId}
              options={cashAccounts.options}
            />
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 150 }}
              value={status}
              onChange={setStatus}
              options={STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
          </>
        }
        createLabel="Lập chứng từ"
        onCreate={can('cash-document:create') ? () => setEditing('new') : undefined}
      />
      <Table<CashDocument> {...tableProps} size="small" columns={columns} scroll={{ x: 1500 }} />
      {selected !== null && (
        <CashDocumentDrawer
          id={selected}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        />
      )}
      <FormModal<CashDocumentForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập chứng từ thu chi' : 'Sửa chứng từ thu chi'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <CashDocumentFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />
        )}
      </FormModal>
    </Card>
  );
}
