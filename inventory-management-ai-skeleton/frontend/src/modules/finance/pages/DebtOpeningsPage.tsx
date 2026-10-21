import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
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
  Row,
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
import { formatMoney } from '../../accounting/labels';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { ImportModal } from '../../masterdata/components/ImportModal';
import { useCompanyId } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { useMyBranches } from '../../system/lookups';
import { debtOpeningApi } from '../api';
import { DEBT_PARTNER_ROLE, DEBT_TYPES } from '../labels';
import type {
  DebtOpening,
  DebtOpeningLine,
  DebtOpeningLineImportResult,
  DebtType,
  FinanceDocumentStatus,
  ImportedDebtOpeningLine,
} from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';
import { BRANCH_VISIBLE, whenBranchVisible } from '../../../shared/branch';

const STATUSES: FinanceDocumentStatus[] = [
  'DRAFT',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

interface LineForm {
  partnerId?: number;
  partnerLabel?: string;
  dueDate?: Dayjs;
  amount?: number;
  note?: string;
}

interface OpeningForm {
  branchId: number;
  debtType: DebtType;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = ['branchId', 'debtType', 'documentDate', 'postingDate', 'description', 'lines'];

/**
 * Thao tác hiện theo trạng thái và quyền; server vẫn kiểm tra lại (frontend.md mục 3). Không qua duyệt (D-60): như
 * financeActions (labels.ts) nhưng đảo dùng quyền ghi sổ.
 */
function openingActions(status: FinanceDocumentStatus, can: (permission: string) => boolean): string[] {
  const cancel = can('debt-opening:create') ? ['cancel'] : [];
  const post = can('debt-opening:post') ? ['post'] : [];
  const actions: Record<FinanceDocumentStatus, string[]> = {
    DRAFT: [...post, ...cancel],
    REJECTED: cancel,
    SUBMITTED: cancel,
    APPROVED: [...post, ...cancel],
    POSTED: can('debt-opening:post') ? ['reverse'] : [],
    CANCELLED: [],
    REVERSED: [],
  };
  return actions[status];
}

const LINE_COLUMNS: TableColumnsType<DebtOpeningLine> = [
  { title: '#', dataIndex: 'lineNo', width: 50 },
  { title: 'Đối tượng', render: (_, line) => `${line.partnerCode ?? ''} - ${line.partnerName ?? ''}` },
  { title: 'Hạn thanh toán', dataIndex: 'dueDate', width: 130, render: formatDate },
  { title: 'Số tiền', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
  { title: 'Ghi chú', dataIndex: 'note' },
];

function OpeningDrawer({
  id,
  onEdit,
  onClose,
}: {
  id: number;
  onEdit(document: DebtOpening): void;
  onClose(): void;
}) {
  const can = useCan();
  const queryClient = useQueryClient();
  const branches = useMyBranches();
  const query = useQuery({ queryKey: ['debt-opening', id], queryFn: () => debtOpeningApi.get(id) });
  const document = query.data;
  const editable = document?.status === 'DRAFT';
  return (
    <Drawer
      open
      width={1000}
      title={document ? `Công nợ đầu kỳ ${document.documentNo}` : ''}
      onClose={onClose}
    >
      {document && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={document.status} />
            <Space wrap>
              {editable && can('debt-opening:create') && (
                <Button onClick={() => onEdit(document)} icon={<EditOutlined />}>
                  Sửa
                </Button>
              )}
              <DocumentActions
                actions={openingActions(document.status, can)}
                onAction={async (action, body, key) => {
                  await debtOpeningApi.act(id, action, body, key);
                  void queryClient.invalidateQueries({ queryKey: ['debt-opening', id] });
                  void queryClient.invalidateQueries({ queryKey: ['debt-openings'] });
                }}
              />
            </Space>
          </Space>
          <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Loại công nợ">{DEBT_TYPES[document.debtType]}</Descriptions.Item>
            {BRANCH_VISIBLE && (
              <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
            )}
            <Descriptions.Item label="Ngày chứng từ">{formatDate(document.documentDate)}</Descriptions.Item>
            <Descriptions.Item label="Ngày hạch toán">{formatDate(document.postingDate)}</Descriptions.Item>
            <Descriptions.Item label="Tổng tiền">{formatMoney(document.totalAmount)}</Descriptions.Item>
            {document.reverseDate && (
              <Descriptions.Item label="Ngày đảo">{formatDate(document.reverseDate)}</Descriptions.Item>
            )}
            {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
            <Descriptions.Item label="Diễn giải" span={2}>
              {document.description}
            </Descriptions.Item>
          </Descriptions>
          <Table<DebtOpeningLine>
            rowKey="id"
            size="small"
            pagination={false}
            columns={LINE_COLUMNS}
            dataSource={document.lines}
            scroll={{ x: 900 }}
          />
          <ApprovalHistory requests={document.approvals} />
        </>
      )}
    </Drawer>
  );
}

/**
 * Công nợ đầu kỳ (BR-MIG-01): nhập số còn phải thu / phải trả theo đối tượng và hạn thanh toán; ghi sổ thẳng từ nháp
 * thành khoản công nợ (D-60). Để trống hạn thanh toán thì tính theo số ngày nợ của đối tượng (BR-AR-02).
 */
export function DebtOpeningsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [debtType, setDebtType] = useState<DebtType>();
  const [status, setStatus] = useState<FinanceDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<DebtOpening | 'new' | null>(null);
  const { tableProps } = useServerTable('debt-openings', debtOpeningApi.list, {
    companyId,
    keyword,
    debtType,
    status,
  });

  const save = async (values: OpeningForm, key: string) => {
    const input = {
      branchId: values.branchId,
      debtType: values.debtType,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        partnerId: line.partnerId,
        dueDate: line.dueDate ? line.dueDate.format('YYYY-MM-DD') : null,
        amount: line.amount,
        note: line.note,
      })),
    };
    if (editing === 'new') {
      const created = await debtOpeningApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await debtOpeningApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['debt-opening', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['debt-openings'] });
  };

  const initialValues: Partial<OpeningForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          debtType: editing.debtType,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            partnerId: line.partnerId,
            partnerLabel: `${line.partnerCode ?? ''} - ${line.partnerName ?? ''}`,
            dueDate: dayjs(line.dueDate),
            amount: line.amount,
            note: line.note ?? undefined,
          })),
        }
      : { branchId: branches.defaultId, debtType: 'RECEIVABLE', documentDate: dayjs(), lines: [{}] };

  const columns: TableColumnsType<DebtOpening> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 200,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 120, sorter: true, render: formatDate },
    { title: 'Loại', dataIndex: 'debtType', width: 110, render: (value: DebtType) => DEBT_TYPES[value] },
    ...whenBranchVisible({
      title: 'Chi nhánh',
      dataIndex: 'branchId',
      width: 160,
      render: (value: number) => branches.nameOf(value),
    }),
    { title: 'Diễn giải', dataIndex: 'description' },
    { title: 'Số dòng', dataIndex: 'lineCount', width: 90, align: 'right' },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 160, align: 'right', render: formatMoney },
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
        Công nợ đầu kỳ
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <>
            <Select
              allowClear
              placeholder="Loại công nợ"
              style={{ width: 150 }}
              value={debtType}
              onChange={setDebtType}
              options={Object.entries(DEBT_TYPES).map(([value, label]) => ({ value, label }))}
            />
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 160 }}
              value={status}
              onChange={setStatus}
              options={STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
          </>
        }
        createLabel="Lập chứng từ"
        onCreate={can('debt-opening:create') ? () => setEditing('new') : undefined}
      />
      <Table<DebtOpening> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <OpeningDrawer
          id={selected}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        />
      )}
      <FormModal<OpeningForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập chứng từ công nợ đầu kỳ' : 'Sửa chứng từ công nợ đầu kỳ'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <OpeningFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
    </Card>
  );
}

function OpeningFields({
  form,
  isNew,
  branchOptions,
}: {
  form: ReturnType<typeof Form.useForm<OpeningForm>>[0];
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const debtType = (Form.useWatch('debtType', form) ?? 'RECEIVABLE') as DebtType;
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
        <Col xs={24} md={6}>
          <Form.Item name="debtType" label="Loại công nợ" rules={[{ required: true, message: 'Chọn loại' }]}>
            <Select
              disabled={!isNew}
              options={Object.entries(DEBT_TYPES).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={6}>
          <Form.Item
            name="documentDate"
            label="Ngày chứng từ"
            rules={[{ required: true, message: 'Chọn ngày' }]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={6}>
          <Form.Item name="postingDate" label="Ngày hạch toán" extra="Để trống: bằng ngày chứng từ">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <Typography.Text type="secondary">
          Mỗi dòng là số còn nợ của một đối tượng đến một hạn thanh toán. Để trống hạn thanh toán: ngày chứng
          từ + số ngày nợ của đối tượng.
        </Typography.Text>
        <LineImportButton form={form} debtType={debtType} />
      </Space>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={8} key={field.key} align="top" style={{ marginTop: 8 }}>
                <Col xs={24} md={8}>
                  <Form.Item
                    name={[field.name, 'partnerId']}
                    label="Đối tượng"
                    rules={[{ required: true, message: 'Chọn đối tượng' }]}
                  >
                    <PartnerSelect
                      role={DEBT_PARTNER_ROLE[debtType]}
                      selectedLabel={form.getFieldValue(['lines', field.name, 'partnerLabel'])}
                    />
                  </Form.Item>
                </Col>
                <Col xs={12} md={4}>
                  <Form.Item name={[field.name, 'dueDate']} label="Hạn thanh toán">
                    <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={5}>
                  <Form.Item
                    name={[field.name, 'amount']}
                    label="Số tiền còn nợ"
                    rules={[{ required: true, message: 'Nhập số tiền' }]}
                  >
                    <InputNumber<number>
                      min={1}
                      style={{ width: '100%' }}
                      formatter={(value) => `${value ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
                      parser={(value) => Number((value ?? '').replace(/\./g, ''))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={20} md={6}>
                  <Form.Item name={[field.name, 'note']} label="Ghi chú">
                    <Input maxLength={500} />
                  </Form.Item>
                </Col>
                <Col xs={4} md={1}>
                  <Button
                    style={{ marginTop: 30 }}
                    icon={<DeleteOutlined />}
                    disabled={fields.length === 1}
                    onClick={() => remove(field.name)}
                  />
                </Col>
              </Row>
            ))}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}

/**
 * Nhập dòng công nợ từ Excel theo loại công nợ đang chọn: server chỉ đọc file và trả dòng, dòng được nối sau các dòng
 * đã chọn đối tượng (dòng trống bị bỏ). Không lưu chứng từ; người dùng kiểm tra rồi bấm Lưu như lập tay.
 */
function LineImportButton({
  form,
  debtType,
}: {
  form: ReturnType<typeof Form.useForm<OpeningForm>>[0];
  debtType: DebtType;
}) {
  const companyId = useCompanyId();
  const [open, setOpen] = useState(false);

  const fillLines = (result: DebtOpeningLineImportResult) => {
    const kept = (form.getFieldValue('lines') ?? []).filter((line: LineForm) => line.partnerId);
    form.setFieldValue('lines', [...kept, ...result.lines.map(toLineForm)]);
  };

  return (
    <>
      <Button icon={<UploadOutlined />} onClick={() => setOpen(true)}>
        Nhập dòng từ Excel
      </Button>
      <ImportModal<DebtOpeningLineImportResult>
        open={open}
        title={`Nhập dòng công nợ đầu kỳ (${DEBT_TYPES[debtType]}) từ Excel`}
        onDownloadTemplate={debtOpeningApi.downloadLineTemplate}
        onImport={(file, key) => debtOpeningApi.importLines(file, companyId, debtType, key)}
        successMessage={(result) => `Đã điền ${result.created} dòng vào chứng từ. Kiểm tra lại rồi bấm Lưu.`}
        onDone={fillLines}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

function toLineForm(line: ImportedDebtOpeningLine): LineForm {
  return {
    partnerId: line.partnerId,
    partnerLabel: `${line.partnerCode} - ${line.partnerName}`,
    dueDate: line.dueDate ? dayjs(line.dueDate) : undefined,
    amount: line.amount,
    note: line.note ?? undefined,
  };
}
