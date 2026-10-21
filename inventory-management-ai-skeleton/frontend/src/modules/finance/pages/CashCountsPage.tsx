import { useQueryClient } from '@tanstack/react-query';
import {
  App,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
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
import { formatMoney } from '../../accounting/labels';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { cashCountApi } from '../api';
import { moneyInputProps } from '../components/debtInputs';
import { FinanceDocumentDrawer } from '../components/FinanceDocumentDrawer';
import { FINANCE_STATUSES } from '../labels';
import { useCashAccounts } from '../lookups';
import type { CashAccount, CashCount, FinanceDocumentStatus } from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';
import { BRANCH_VISIBLE } from '../../../shared/branch';

interface CountForm {
  branchId: number;
  cashAccountId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  countedAmount?: number;
  description?: string;
}

const FIELDS = ['branchId', 'cashAccountId', 'documentDate', 'postingDate', 'countedAmount', 'description'];

/**
 * Kiểm kê quỹ tiền mặt (KKQ, BR-CSH-05): nhập số tiền đếm thực tế; ghi sổ (D-61 không qua duyệt) so với số dư sổ quỹ đến hết
 * ngày kiểm kê và ghi chênh lệch thừa / thiếu.
 */
export function CashCountsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const cashAccounts = useCashAccounts();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<FinanceDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<CashCount | 'new' | null>(null);
  const { tableProps } = useServerTable('cash-counts', cashCountApi.list, { companyId, keyword, status });

  const save = async (values: CountForm, key: string) => {
    const input = {
      branchId: values.branchId,
      cashAccountId: values.cashAccountId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      countedAmount: values.countedAmount,
      description: values.description,
    };
    if (editing === 'new') {
      const created = await cashCountApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await cashCountApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu phiếu kiểm kê.');
      void queryClient.invalidateQueries({ queryKey: ['cash-count', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['cash-counts'] });
  };

  const initialValues: Partial<CountForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          cashAccountId: editing.cashAccountId,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          countedAmount: editing.countedAmount,
          description: editing.description ?? undefined,
        }
      : { branchId: branches.defaultId, documentDate: dayjs() };

  const columns: TableColumnsType<CashCount> = [
    {
      title: 'Số phiếu',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày kiểm kê', dataIndex: 'documentDate', width: 130, sorter: true, render: formatDate },
    {
      title: 'Quỹ',
      width: 220,
      render: (_, row) => `${row.cashAccountCode ?? ''} - ${row.cashAccountName ?? ''}`,
    },
    { title: 'Thực đếm', dataIndex: 'countedAmount', width: 150, align: 'right', render: formatMoney },
    { title: 'Sổ quỹ', dataIndex: 'bookBalance', width: 150, align: 'right', render: formatMoney },
    { title: 'Chênh lệch', dataIndex: 'differenceAmount', width: 140, align: 'right', render: formatMoney },
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
        Kiểm kê quỹ
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số phiếu, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 150 }}
            value={status}
            onChange={setStatus}
            options={FINANCE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập phiếu kiểm kê"
        onCreate={can('cash-count:create') ? () => setEditing('new') : undefined}
      />
      <Table<CashCount> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <FinanceDocumentDrawer<CashCount>
          id={selected}
          queryKey="cash-count"
          listKeys={['cash-counts', 'cash-accounts']}
          load={cashCountApi.get}
          act={cashCountApi.act}
          permission="cash-count"
          title={(count) => `Kiểm kê quỹ ${count.documentNo}`}
          journalSource={(count) => (count.differenceAmount ? 'KKQ' : null)}
          onEdit={(count) => setEditing(count)}
          onClose={() => setSelected(null)}
        >
          {(count) => (
            <Descriptions size="small" column={{ xs: 1, md: 2 }}>
              <Descriptions.Item label="Quỹ">{`${count.cashAccountCode ?? ''} - ${count.cashAccountName ?? ''}`}</Descriptions.Item>
              {BRANCH_VISIBLE && (
                <Descriptions.Item label="Chi nhánh">{branches.nameOf(count.branchId)}</Descriptions.Item>
              )}
              <Descriptions.Item label="Ngày kiểm kê">{formatDate(count.documentDate)}</Descriptions.Item>
              <Descriptions.Item label="Ngày hạch toán">{formatDate(count.postingDate)}</Descriptions.Item>
              <Descriptions.Item label="Thực đếm">{formatMoney(count.countedAmount)}</Descriptions.Item>
              {count.bookBalance === null ? (
                <Descriptions.Item label="Sổ quỹ hiện tại (tham khảo)">
                  {`${formatMoney(count.currentBookBalance)}; chênh lệch ${formatMoney(
                    count.countedAmount - (count.currentBookBalance ?? 0),
                  )}`}
                </Descriptions.Item>
              ) : (
                <Descriptions.Item label="Sổ quỹ khi ghi sổ / chênh lệch">
                  {`${formatMoney(count.bookBalance)} / ${formatMoney(count.differenceAmount)}`}
                </Descriptions.Item>
              )}
              {count.reason && <Descriptions.Item label="Lý do">{count.reason}</Descriptions.Item>}
              <Descriptions.Item label="Diễn giải" span={2}>
                {count.description}
              </Descriptions.Item>
            </Descriptions>
          )}
        </FinanceDocumentDrawer>
      )}
      <FormModal<CountForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập phiếu kiểm kê quỹ' : 'Sửa phiếu kiểm kê quỹ'}
        width={720}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <CountFields
            form={form}
            isNew={editing === 'new'}
            branchOptions={branches.options}
            cashAccounts={cashAccounts.items}
          />
        )}
      </FormModal>
    </Card>
  );
}

function CountFields({
  form,
  isNew,
  branchOptions,
  cashAccounts,
}: {
  form: ReturnType<typeof Form.useForm<CountForm>>[0];
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
  cashAccounts: CashAccount[];
}) {
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const options = cashAccounts
    .filter((item) => item.branchId === branchId && item.accountType === 'CASH')
    .map((item) => ({ value: item.id, label: `${item.code} - ${item.name}` }));
  return (
    <Row gutter={12}>
      <BranchCol xs={24} md={12}>
        <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true, message: 'Chọn chi nhánh' }]}>
          <Select disabled={!isNew} options={branchOptions} />
        </Form.Item>
      </BranchCol>
      <Col xs={24} md={12}>
        <Form.Item
          name="cashAccountId"
          label="Quỹ tiền mặt"
          rules={[{ required: true, message: 'Chọn quỹ' }]}
        >
          <Select options={options} />
        </Form.Item>
      </Col>
      <Col xs={12} md={6}>
        <Form.Item
          name="documentDate"
          label="Ngày kiểm kê"
          rules={[{ required: true, message: 'Chọn ngày' }]}
        >
          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col xs={12} md={6}>
        <Form.Item name="postingDate" label="Ngày hạch toán">
          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item
          name="countedAmount"
          label="Số tiền thực đếm"
          rules={[{ required: true, message: 'Nhập số tiền' }]}
        >
          <InputNumber<number> min={0} {...moneyInputProps} />
        </Form.Item>
      </Col>
      <Col xs={24}>
        <Form.Item name="description" label="Diễn giải">
          <Input maxLength={500} />
        </Form.Item>
      </Col>
    </Row>
  );
}
