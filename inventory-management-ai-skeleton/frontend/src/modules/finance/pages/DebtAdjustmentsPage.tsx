import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
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
import { usePostableAccounts } from '../../accounting/lookups';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useReferenceItems } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { debtAdjustmentApi, reconciliationApi } from '../api';
import { moneyInputProps, useOpenDebtItemOptions } from '../components/debtInputs';
import { FinanceDocumentDrawer } from '../components/FinanceDocumentDrawer';
import { ADJUSTMENT_DOCUMENT_TYPES, DEBT_PARTNER_ROLE, DEBT_TYPES, FINANCE_STATUSES } from '../labels';
import type {
  AdjustmentDirection,
  AdjustmentDocumentType,
  DebtAdjustment,
  DebtAdjustmentLine,
  DebtType,
  FinanceDocumentStatus,
} from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';
import { BRANCH_VISIBLE } from '../../../shared/branch';

interface LineForm {
  direction: AdjustmentDirection;
  debtItemId?: number;
  accountId?: number;
  costItemId?: number;
  amount?: number;
  invoiceNo?: string;
  dueDate?: Dayjs;
  description?: string;
}

interface AdjustmentForm {
  branchId: number;
  debtType: DebtType;
  partnerId?: number;
  partnerLabel?: string;
  reconciliationId?: number;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = [
  'branchId',
  'debtType',
  'partnerId',
  'reconciliationId',
  'documentDate',
  'postingDate',
  'description',
  'lines',
];

const DIRECTIONS: Record<AdjustmentDirection, string> = { INCREASE: 'Tăng', DECREASE: 'Giảm' };

const LINE_COLUMNS: TableColumnsType<DebtAdjustmentLine> = [
  { title: '#', dataIndex: 'lineNo', width: 50 },
  {
    title: 'Tăng / giảm',
    dataIndex: 'direction',
    width: 100,
    render: (value: AdjustmentDirection) => DIRECTIONS[value],
  },
  {
    title: 'Hóa đơn',
    width: 180,
    render: (_, line) => line.debtItemLabel ?? line.invoiceNo ?? '(số chứng từ)',
  },
  {
    title: 'TK đối ứng',
    width: 220,
    render: (_, line) => `${line.accountCode ?? ''} - ${line.accountName ?? ''}`,
  },
  { title: 'Số tiền', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
  { title: 'Diễn giải', dataIndex: 'description' },
];

/**
 * Điều chỉnh công nợ (DCCN, BR-AR-07: xử lý chênh lệch đối chiếu) hoặc thanh toán tạm ứng (TTTU,
 * BR-CSH-04: chi phí thực tế trừ vào tạm ứng nhân viên).
 */
export function DebtAdjustmentsPage({ documentType }: { documentType: AdjustmentDocumentType }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const advance = documentType === 'TTTU';
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<FinanceDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<DebtAdjustment | 'new' | null>(null);
  const { tableProps } = useServerTable(`debt-adjustments-${documentType}`, debtAdjustmentApi.list, {
    companyId,
    documentType,
    keyword,
    status,
  });

  const save = async (values: AdjustmentForm, key: string) => {
    const input = {
      branchId: values.branchId,
      documentType,
      debtType: advance ? 'ADVANCE' : values.debtType,
      partnerId: values.partnerId,
      reconciliationId: values.reconciliationId ?? null,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        direction: advance ? 'DECREASE' : line.direction,
        debtItemId: line.debtItemId ?? null,
        accountId: line.accountId,
        costItemId: line.costItemId ?? null,
        amount: line.amount,
        invoiceNo: line.invoiceNo,
        dueDate: line.dueDate ? line.dueDate.format('YYYY-MM-DD') : null,
        description: line.description,
      })),
    };
    if (editing === 'new') {
      const created = await debtAdjustmentApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await debtAdjustmentApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['debt-adjustment', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: [`debt-adjustments-${documentType}`] });
  };

  const initialValues: Partial<AdjustmentForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          debtType: editing.debtType,
          partnerId: editing.partnerId,
          partnerLabel: `${editing.partnerCode ?? ''} - ${editing.partnerName ?? ''}`,
          reconciliationId: editing.reconciliationId ?? undefined,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            direction: line.direction,
            debtItemId: line.debtItemId ?? undefined,
            accountId: line.accountId,
            costItemId: line.costItemId ?? undefined,
            amount: line.amount,
            invoiceNo: line.invoiceNo ?? undefined,
            dueDate: line.dueDate ? dayjs(line.dueDate) : undefined,
            description: line.description ?? undefined,
          })),
        }
      : {
          branchId: branches.defaultId,
          debtType: advance ? 'ADVANCE' : 'RECEIVABLE',
          documentDate: dayjs(),
          lines: [{ direction: 'DECREASE' }],
        };

  const columns: TableColumnsType<DebtAdjustment> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    {
      title: 'Loại công nợ',
      dataIndex: 'debtType',
      width: 120,
      render: (value: DebtType) => DEBT_TYPES[value],
    },
    { title: 'Đối tượng', render: (_, row) => `${row.partnerCode ?? ''} - ${row.partnerName ?? ''}` },
    { title: 'Diễn giải', dataIndex: 'description' },
    { title: 'Số tiền', dataIndex: 'totalAmount', width: 150, align: 'right', render: formatMoney },
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
        {ADJUSTMENT_DOCUMENT_TYPES[documentType]}
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
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
        createLabel="Lập chứng từ"
        onCreate={can('debt-adjustment:create') ? () => setEditing('new') : undefined}
      />
      <Table<DebtAdjustment> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <FinanceDocumentDrawer<DebtAdjustment>
          id={selected}
          queryKey="debt-adjustment"
          listKeys={[`debt-adjustments-${documentType}`, 'debt-items']}
          load={debtAdjustmentApi.get}
          act={debtAdjustmentApi.act}
          permission="debt-adjustment"
          title={(document) => `${ADJUSTMENT_DOCUMENT_TYPES[document.documentType]} ${document.documentNo}`}
          journalSource={(document) => document.documentType}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Loại công nợ">{DEBT_TYPES[document.debtType]}</Descriptions.Item>
                <Descriptions.Item label="Đối tượng">{`${document.partnerCode ?? ''} - ${document.partnerName ?? ''}`}</Descriptions.Item>
                {BRANCH_VISIBLE && (
                  <Descriptions.Item label="Chi nhánh">
                    {branches.nameOf(document.branchId)}
                  </Descriptions.Item>
                )}
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Tổng tiền">{formatMoney(document.totalAmount)}</Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={2}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<DebtAdjustmentLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={LINE_COLUMNS}
                dataSource={document.lines}
                scroll={{ x: 900 }}
              />
            </>
          )}
        </FinanceDocumentDrawer>
      )}
      <FormModal<AdjustmentForm>
        open={editing !== null}
        title={`${editing === 'new' ? 'Lập' : 'Sửa'} ${ADJUSTMENT_DOCUMENT_TYPES[documentType].toLowerCase()}`}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <AdjustmentFields
            form={form}
            isNew={editing === 'new'}
            advance={advance}
            branchOptions={branches.options}
          />
        )}
      </FormModal>
    </Card>
  );
}

function AdjustmentFields({
  form,
  isNew,
  advance,
  branchOptions,
}: {
  form: ReturnType<typeof Form.useForm<AdjustmentForm>>[0];
  isNew: boolean;
  advance: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const companyId = useCompanyId();
  const accounts = usePostableAccounts();
  const costItems = useReferenceItems('COST_ITEM');
  const debtType = (Form.useWatch('debtType', form) ?? 'RECEIVABLE') as DebtType;
  const partnerId = Form.useWatch('partnerId', form) as number | undefined;
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const lines = (Form.useWatch('lines', form) ?? []) as LineForm[];
  const items = useOpenDebtItemOptions([debtType], partnerId, branchId);
  const reconciliations = useQuery({
    queryKey: ['debt-reconciliations', 'lookup', companyId, debtType, partnerId],
    queryFn: () =>
      reconciliationApi.list({ page: 0, size: 50, companyId, debtType, partnerId, status: 'DIFFERENT' }),
    enabled: !advance && !!partnerId,
  });
  return (
    <>
      <Row gutter={12}>
        <BranchCol xs={24} md={6}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </BranchCol>
        {!advance && (
          <Col xs={24} md={6}>
            <Form.Item
              name="debtType"
              label="Loại công nợ"
              rules={[{ required: true, message: 'Chọn loại' }]}
            >
              <Select
                disabled={!isNew}
                options={Object.entries(DEBT_TYPES).map(([value, label]) => ({ value, label }))}
                onChange={() =>
                  form.setFieldsValue({
                    partnerId: undefined,
                    partnerLabel: undefined,
                    lines: [{ direction: 'DECREASE' }],
                  })
                }
              />
            </Form.Item>
          </Col>
        )}
        <Col xs={12} md={3}>
          <Form.Item
            name="documentDate"
            label="Ngày chứng từ"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item name="postingDate" label="Ngày hạch toán">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={advance ? 12 : 6}>
          <Form.Item
            name="partnerId"
            label={advance ? 'Nhân viên' : 'Đối tượng'}
            rules={[{ required: true, message: 'Chọn đối tượng' }]}
          >
            <PartnerSelect
              role={DEBT_PARTNER_ROLE[advance ? 'ADVANCE' : debtType]}
              selectedLabel={form.getFieldValue('partnerLabel')}
            />
          </Form.Item>
        </Col>
        {!advance && (
          <Col xs={24} md={12}>
            <Form.Item name="reconciliationId" label="Biên bản đối chiếu (lệch)">
              <Select
                allowClear
                options={(reconciliations.data?.items ?? []).map((item) => ({
                  value: item.id,
                  label: `${item.documentNo} - ngày ${formatDate(item.asOfDate)} - lệch ${formatMoney(item.difference)}`,
                }))}
              />
            </Form.Item>
          </Col>
        )}
        <Col xs={24} md={12}>
          <Form.Item name="description" label="Diễn giải / lý do">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => {
              const increase = !advance && lines[field.name]?.direction === 'INCREASE';
              return (
                <Row gutter={8} key={field.key}>
                  {!advance && (
                    <Col xs={8} md={3}>
                      <Form.Item
                        name={[field.name, 'direction']}
                        label="Tăng / giảm"
                        rules={[{ required: true }]}
                      >
                        <Select
                          options={Object.entries(DIRECTIONS).map(([value, label]) => ({ value, label }))}
                        />
                      </Form.Item>
                    </Col>
                  )}
                  {increase ? (
                    <>
                      <Col xs={16} md={4}>
                        <Form.Item name={[field.name, 'invoiceNo']} label="Số hóa đơn (nếu có)">
                          <Input maxLength={50} />
                        </Form.Item>
                      </Col>
                      <Col xs={12} md={3}>
                        <Form.Item name={[field.name, 'dueDate']} label="Hạn">
                          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                    </>
                  ) : (
                    <Col xs={16} md={advance ? 9 : 7}>
                      <Form.Item
                        name={[field.name, 'debtItemId']}
                        label={advance ? 'Khoản tạm ứng' : 'Hóa đơn giảm'}
                        rules={[{ required: true, message: 'Chọn hóa đơn' }]}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          options={items.options}
                          loading={items.loading}
                        />
                      </Form.Item>
                    </Col>
                  )}
                  <Col xs={12} md={advance ? 5 : 4}>
                    <Form.Item
                      name={[field.name, 'accountId']}
                      label={advance ? 'TK chi phí' : 'TK đối ứng'}
                      rules={[{ required: true, message: 'Chọn tài khoản' }]}
                    >
                      <Select showSearch optionFilterProp="label" options={accounts.options} />
                    </Form.Item>
                  </Col>
                  <Col xs={12} md={3}>
                    <Form.Item name={[field.name, 'costItemId']} label="Khoản mục">
                      <Select allowClear showSearch optionFilterProp="label" options={costItems.options} />
                    </Form.Item>
                  </Col>
                  <Col xs={12} md={3}>
                    <Form.Item
                      name={[field.name, 'amount']}
                      label="Số tiền"
                      rules={[{ required: true, message: 'Nhập số tiền' }]}
                    >
                      <InputNumber<number> min={1} {...moneyInputProps} />
                    </Form.Item>
                  </Col>
                  <Col xs={10} md={advance ? 3 : 2}>
                    <Form.Item name={[field.name, 'description']} label="Diễn giải">
                      <Input maxLength={500} />
                    </Form.Item>
                  </Col>
                  <Col xs={2} md={1}>
                    <Button
                      style={{ marginTop: 30 }}
                      icon={<DeleteOutlined />}
                      disabled={fields.length === 1}
                      onClick={() => remove(field.name)}
                    />
                  </Col>
                </Row>
              );
            })}
            <Button
              type="dashed"
              block
              icon={<PlusOutlined />}
              onClick={() => add({ direction: 'DECREASE' })}
            >
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
