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
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { debtApi, debtOffsetApi } from '../api';
import { moneyInputProps, useOpenDebtItemOptions } from '../components/debtInputs';
import { FinanceDocumentDrawer } from '../components/FinanceDocumentDrawer';
import { DEBT_PARTNER_ROLE, DEBT_TYPES, FINANCE_STATUSES, OFFSET_TYPES } from '../labels';
import type { DebtOffset, DebtOffsetLine, DebtType, FinanceDocumentStatus, OffsetType } from '../types';

interface OffsetForm {
  branchId: number;
  offsetType: OffsetType;
  debtType?: DebtType;
  partnerId?: number;
  partnerLabel?: string;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  description?: string;
  lines: { debtItemId?: number; amount?: number; description?: string }[];
}

const FIELDS = [
  'branchId',
  'offsetType',
  'debtType',
  'partnerId',
  'documentDate',
  'postingDate',
  'description',
  'lines',
];

const LINE_COLUMNS: TableColumnsType<DebtOffsetLine> = [
  { title: '#', dataIndex: 'lineNo', width: 50 },
  {
    title: 'Loại',
    dataIndex: 'debtType',
    width: 110,
    render: (value: DebtType | null) => (value ? DEBT_TYPES[value] : ''),
  },
  { title: 'Hóa đơn', dataIndex: 'invoiceNo', width: 180 },
  { title: 'Hạn', dataIndex: 'dueDate', width: 120, render: formatDate },
  {
    title: 'Còn lại hiện tại',
    dataIndex: 'remainingAmount',
    width: 150,
    align: 'right',
    render: formatMoney,
  },
  { title: 'Bù trừ', dataIndex: 'amount', width: 150, align: 'right', render: formatMoney },
  { title: 'Ghi chú', dataIndex: 'description' },
];

/**
 * Bù trừ công nợ (DTCN, BR-AR-04): dùng tiền trả trước trừ vào hóa đơn, hoặc trừ phải thu với phải trả của cùng đối
 * tượng. Bỏ bù trừ bằng đảo chứng từ.
 */
export function DebtOffsetsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [offsetType, setOffsetType] = useState<OffsetType>();
  const [status, setStatus] = useState<FinanceDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<DebtOffset | 'new' | null>(null);
  const { tableProps } = useServerTable('debt-offsets', debtOffsetApi.list, {
    companyId,
    keyword,
    offsetType,
    status,
  });

  const save = async (values: OffsetForm, key: string) => {
    const input = {
      branchId: values.branchId,
      offsetType: values.offsetType,
      debtType: values.offsetType === 'PREPAYMENT' ? values.debtType : null,
      partnerId: values.partnerId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      postingDate: values.postingDate ? values.postingDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        debtItemId: line.debtItemId,
        amount: line.amount,
        description: line.description,
      })),
    };
    if (editing === 'new') {
      const created = await debtOffsetApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await debtOffsetApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu chứng từ.');
      void queryClient.invalidateQueries({ queryKey: ['debt-offset', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['debt-offsets'] });
  };

  const initialValues: Partial<OffsetForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          offsetType: editing.offsetType,
          debtType: editing.debtType ?? undefined,
          partnerId: editing.partnerId,
          partnerLabel: `${editing.partnerCode ?? ''} - ${editing.partnerName ?? ''}`,
          documentDate: dayjs(editing.documentDate),
          postingDate: dayjs(editing.postingDate),
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            debtItemId: line.debtItemId,
            amount: line.amount,
            description: line.description ?? undefined,
          })),
        }
      : {
          branchId: branches.items[0]?.id,
          offsetType: 'PREPAYMENT',
          debtType: 'RECEIVABLE',
          documentDate: dayjs(),
          lines: [{}],
        };

  const columns: TableColumnsType<DebtOffset> = [
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
      dataIndex: 'offsetType',
      width: 190,
      render: (value: OffsetType) => OFFSET_TYPES[value],
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
        Bù trừ công nợ
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <>
            <Select
              allowClear
              placeholder="Loại bù trừ"
              style={{ width: 200 }}
              value={offsetType}
              onChange={setOffsetType}
              options={Object.entries(OFFSET_TYPES).map(([value, label]) => ({ value, label }))}
            />
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 150 }}
              value={status}
              onChange={setStatus}
              options={FINANCE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
          </>
        }
        createLabel="Lập chứng từ"
        onCreate={can('debt-offset:create') ? () => setEditing('new') : undefined}
      />
      <Table<DebtOffset> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <FinanceDocumentDrawer<DebtOffset>
          id={selected}
          queryKey="debt-offset"
          listKeys={['debt-offsets', 'debt-items']}
          load={debtOffsetApi.get}
          act={debtOffsetApi.act}
          permission="debt-offset"
          title={(document) => `Bù trừ công nợ ${document.documentNo}`}
          journalSource={(document) => (document.offsetType === 'AR_AP' ? 'DTCN' : null)}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Loại">{OFFSET_TYPES[document.offsetType]}</Descriptions.Item>
                {document.debtType && (
                  <Descriptions.Item label="Loại công nợ">{DEBT_TYPES[document.debtType]}</Descriptions.Item>
                )}
                <Descriptions.Item label="Đối tượng">{`${document.partnerCode ?? ''} - ${document.partnerName ?? ''}`}</Descriptions.Item>
                <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
                <Descriptions.Item label="Ngày hạch toán">
                  {formatDate(document.postingDate)}
                </Descriptions.Item>
                <Descriptions.Item label="Số tiền bù trừ">
                  {formatMoney(document.totalAmount)}
                </Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={2}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<DebtOffsetLine>
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
      <FormModal<OffsetForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập chứng từ bù trừ công nợ' : 'Sửa chứng từ bù trừ công nợ'}
        width={1000}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => <OffsetFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />}
      </FormModal>
    </Card>
  );
}

function OffsetFields({
  form,
  isNew,
  branchOptions,
}: {
  form: ReturnType<typeof Form.useForm<OffsetForm>>[0];
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const companyId = useCompanyId();
  const offsetType = (Form.useWatch('offsetType', form) ?? 'PREPAYMENT') as OffsetType;
  const debtType = (Form.useWatch('debtType', form) ?? 'RECEIVABLE') as DebtType;
  const partnerId = Form.useWatch('partnerId', form) as number | undefined;
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const types: DebtType[] = offsetType === 'AR_AP' ? ['RECEIVABLE', 'PAYABLE'] : [debtType];
  const items = useOpenDebtItemOptions(types, partnerId, branchId);
  const prepayments = useQuery({
    queryKey: ['debt-prepayments', companyId, debtType, partnerId],
    queryFn: () => debtApi.prepayments({ companyId, debtType, partnerId: partnerId as number }),
    enabled: offsetType === 'PREPAYMENT' && !!partnerId,
  });
  const available = (prepayments.data ?? []).find((row) => row.branchId === branchId)?.amount ?? 0;
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={6}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item name="offsetType" label="Loại bù trừ" rules={[{ required: true, message: 'Chọn loại' }]}>
            <Select
              disabled={!isNew}
              options={Object.entries(OFFSET_TYPES).map(([value, label]) => ({ value, label }))}
              onChange={() =>
                form.setFieldsValue({ lines: [{}], partnerId: undefined, partnerLabel: undefined })
              }
            />
          </Form.Item>
        </Col>
        {offsetType === 'PREPAYMENT' && (
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
                  form.setFieldsValue({ lines: [{}], partnerId: undefined, partnerLabel: undefined })
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
        <Col xs={24} md={12}>
          <Form.Item
            name="partnerId"
            label="Đối tượng"
            rules={[{ required: true, message: 'Chọn đối tượng' }]}
            extra={
              offsetType === 'PREPAYMENT'
                ? `Tiền trả trước còn tại chi nhánh: ${formatMoney(available)}`
                : 'Đối tượng vừa là khách hàng vừa là nhà cung cấp; tổng phải thu bù trừ = tổng phải trả bù trừ'
            }
          >
            <PartnerSelect
              role={offsetType === 'PREPAYMENT' ? DEBT_PARTNER_ROLE[debtType] : 'CUSTOMER'}
              selectedLabel={form.getFieldValue('partnerLabel')}
              onChange={() => form.setFieldsValue({ lines: [{}] })}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <Row gutter={8} key={field.key}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name={[field.name, 'debtItemId']}
                    label="Hóa đơn"
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
                <Col xs={12} md={5}>
                  <Form.Item
                    name={[field.name, 'amount']}
                    label="Số tiền"
                    rules={[{ required: true, message: 'Nhập số tiền' }]}
                  >
                    <InputNumber<number> min={1} {...moneyInputProps} />
                  </Form.Item>
                </Col>
                <Col xs={10} md={6}>
                  <Form.Item name={[field.name, 'description']} label="Ghi chú">
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
            ))}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              Thêm hóa đơn
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
