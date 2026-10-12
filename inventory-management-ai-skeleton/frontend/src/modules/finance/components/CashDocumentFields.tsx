import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Col, DatePicker, Form, Input, InputNumber, Radio, Row, Select, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import { formatMoney } from '../../accounting/labels';
import { usePostableAccounts } from '../../accounting/lookups';
import { formatDate } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useReferenceItems } from '../../masterdata/lookups';
import { debtApi } from '../api';
import {
  ALLOCATION_MODES,
  CASH_DOCUMENT_TYPES,
  CASH_PURPOSES,
  CASH_TYPE_RULES,
  DEBT_PARTNER_ROLE,
  PURPOSE_CREATED_DEBT,
  PURPOSE_DEBT_TYPE,
} from '../labels';
import { useCashAccounts } from '../lookups';
import type { AllocationMode, CashDocumentType, CashPurpose, DebtType } from '../types';

export interface CashLineForm {
  debtItemId?: number;
  accountId?: number;
  costItemId?: number;
  amount?: number;
  description?: string;
}

export interface CashDocumentForm {
  branchId: number;
  documentType: CashDocumentType;
  purpose: CashPurpose;
  documentDate: Dayjs;
  postingDate?: Dayjs;
  cashAccountId?: number;
  targetCashAccountId?: number;
  partnerId?: number;
  partnerLabel?: string;
  counterpartyName?: string;
  allocationMode?: AllocationMode;
  amount?: number;
  description?: string;
  lines: CashLineForm[];
}

const moneyInput = {
  min: 1,
  style: { width: '100%' },
  formatter: (value: number | undefined) => `${value ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (value: string | undefined) => Number((value ?? '').replace(/\./g, '')),
};

/**
 * Trường của chứng từ thu chi theo loại và nội dung: thu tiền khách / trả nhà cung cấp chọn cách phân bổ (tự động
 * hóa đơn đến hạn sớm nhất trước, hoặc chỉ định hóa đơn); thu chi khác nhập tài khoản đối ứng theo dòng; chuyển tiền
 * chọn quỹ nhận.
 */
export function CashDocumentFields({
  form,
  isNew,
  branchOptions,
}: {
  form: ReturnType<typeof Form.useForm<CashDocumentForm>>[0];
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  const cashAccounts = useCashAccounts();
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const documentType = (Form.useWatch('documentType', form) ?? 'PT') as CashDocumentType;
  const purpose = (Form.useWatch('purpose', form) ??
    CASH_TYPE_RULES[documentType].purposes[0]) as CashPurpose;
  const cashAccountId = Form.useWatch('cashAccountId', form) as number | undefined;
  const allocationMode = Form.useWatch('allocationMode', form) as AllocationMode | undefined;
  const rule = CASH_TYPE_RULES[documentType];
  const debtType = PURPOSE_DEBT_TYPE[purpose];
  const createdDebt = PURPOSE_CREATED_DEBT[purpose];
  const partnerDebt = debtType ?? createdDebt;
  const sourceOptions = cashAccounts.items
    .filter((item) => item.branchId === branchId)
    .filter((item) => rule.accountType === null || item.accountType === rule.accountType)
    .map((item) => ({ value: item.id, label: `${item.code} - ${item.name}` }));
  const targetOptions = cashAccounts.options.filter((option) => option.value !== cashAccountId);

  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={6}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select
              disabled={!isNew}
              options={branchOptions}
              onChange={() => form.setFieldsValue({ cashAccountId: undefined })}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="documentType"
            label="Loại chứng từ"
            rules={[{ required: true, message: 'Chọn loại' }]}
          >
            <Select
              disabled={!isNew}
              options={Object.entries(CASH_DOCUMENT_TYPES).map(([value, label]) => ({ value, label }))}
              onChange={(value: CashDocumentType) =>
                form.setFieldsValue({
                  purpose: CASH_TYPE_RULES[value].purposes[0],
                  cashAccountId: undefined,
                  lines: [],
                })
              }
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item name="purpose" label="Nội dung" rules={[{ required: true, message: 'Chọn nội dung' }]}>
            <Select
              disabled={!isNew}
              options={rule.purposes.map((value) => ({ value, label: CASH_PURPOSES[value] }))}
              onChange={() =>
                form.setFieldsValue({ lines: [], partnerId: undefined, partnerLabel: undefined })
              }
            />
          </Form.Item>
        </Col>
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
        <Col xs={24} md={8}>
          <Form.Item
            name="cashAccountId"
            label={purpose === 'INTERNAL_TRANSFER' ? 'Quỹ chuyển' : 'Quỹ / tài khoản'}
            rules={[{ required: true, message: 'Chọn quỹ' }]}
          >
            <Select showSearch optionFilterProp="label" options={sourceOptions} />
          </Form.Item>
        </Col>
        {purpose === 'INTERNAL_TRANSFER' && (
          <Col xs={24} md={8}>
            <Form.Item
              name="targetCashAccountId"
              label="Quỹ nhận"
              rules={[{ required: true, message: 'Chọn quỹ nhận' }]}
            >
              <Select showSearch optionFilterProp="label" options={targetOptions} />
            </Form.Item>
          </Col>
        )}
        {purpose !== 'INTERNAL_TRANSFER' && (
          <Col xs={24} md={8}>
            <Form.Item
              name="partnerId"
              label={partnerDebt ? 'Đối tượng' : 'Đối tượng (nếu có)'}
              rules={partnerDebt ? [{ required: true, message: 'Chọn đối tượng' }] : []}
            >
              <PartnerSelect
                allowClear={!partnerDebt}
                role={partnerDebt ? DEBT_PARTNER_ROLE[partnerDebt] : undefined}
                selectedLabel={form.getFieldValue('partnerLabel')}
                onChange={() => form.setFieldsValue({ lines: [] })}
              />
            </Form.Item>
          </Col>
        )}
        {purpose !== 'INTERNAL_TRANSFER' && (
          <Col xs={24} md={8}>
            <Form.Item
              name="counterpartyName"
              label={rule.purposes.includes('OTHER_RECEIPT') ? 'Người nộp' : 'Người nhận'}
            >
              <Input maxLength={255} />
            </Form.Item>
          </Col>
        )}
        {(partnerDebt || purpose === 'INTERNAL_TRANSFER') && (
          <Col xs={24} md={8}>
            <Form.Item name="amount" label="Số tiền" rules={[{ required: true, message: 'Nhập số tiền' }]}>
              <InputNumber<number> {...moneyInput} />
            </Form.Item>
          </Col>
        )}
        {debtType && (
          <Col xs={24} md={16}>
            <Form.Item
              name="allocationMode"
              label="Phân bổ"
              rules={[{ required: true, message: 'Chọn cách phân bổ' }]}
            >
              <Radio.Group
                options={Object.entries(ALLOCATION_MODES).map(([value, label]) => ({ value, label }))}
                onChange={() => form.setFieldsValue({ lines: [] })}
              />
            </Form.Item>
          </Col>
        )}
        <Col xs={24}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      {debtType && allocationMode === 'MANUAL' && (
        <AllocationLines form={form} branchId={branchId} debtType={debtType} />
      )}
      {debtType && allocationMode === 'AUTO' && (
        <Typography.Text type="secondary">
          Khi ghi sổ, tiền được trừ vào hóa đơn đến hạn sớm nhất trước; phần còn lại ghi nhận trả trước.
        </Typography.Text>
      )}
      {!partnerDebt && purpose !== 'INTERNAL_TRANSFER' && <CounterLines />}
    </>
  );
}

/** Dòng phân bổ chỉ định: hóa đơn còn nợ của đối tượng trong chi nhánh; phần chưa phân bổ là trả trước. */
function AllocationLines({
  form,
  branchId,
  debtType,
}: {
  form: ReturnType<typeof Form.useForm<CashDocumentForm>>[0];
  branchId: number | undefined;
  debtType: DebtType;
}) {
  const companyId = useCompanyId();
  const partnerId = Form.useWatch('partnerId', form) as number | undefined;
  const items = useQuery({
    queryKey: ['debt-items', 'open', companyId, debtType, partnerId, branchId],
    queryFn: () =>
      debtApi.items({
        page: 0,
        size: 200,
        sort: 'dueDate,asc',
        companyId,
        debtType,
        partnerId,
        branchId,
        openOnly: true,
      }),
    enabled: companyId > 0 && !!partnerId && !!branchId,
  });
  const options = (items.data?.items ?? []).map((item) => ({
    value: item.id,
    label: `${item.invoiceNo ?? item.sourceNo} - hạn ${formatDate(item.dueDate)} - còn ${formatMoney(item.remainingAmount)}`,
  }));
  return (
    <Form.List name="lines">
      {(fields, { add, remove }) => (
        <>
          <Typography.Text type="secondary">
            Chọn hóa đơn và số tiền trả cho từng hóa đơn; phần còn lại của số tiền ghi nhận trả trước.
          </Typography.Text>
          {fields.map((field) => (
            <Row gutter={8} key={field.key} style={{ marginTop: 8 }}>
              <Col xs={24} md={12}>
                <Form.Item
                  name={[field.name, 'debtItemId']}
                  label="Hóa đơn"
                  rules={[{ required: true, message: 'Chọn hóa đơn' }]}
                >
                  <Select showSearch optionFilterProp="label" options={options} loading={items.isFetching} />
                </Form.Item>
              </Col>
              <Col xs={12} md={5}>
                <Form.Item
                  name={[field.name, 'amount']}
                  label="Số tiền"
                  rules={[{ required: true, message: 'Nhập số tiền' }]}
                >
                  <InputNumber<number> {...moneyInput} />
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
                  onClick={() => remove(field.name)}
                />
              </Col>
            </Row>
          ))}
          <Button
            type="dashed"
            block
            icon={<PlusOutlined />}
            onClick={() => add({})}
            style={{ marginTop: 8 }}
          >
            Thêm hóa đơn
          </Button>
        </>
      )}
    </Form.List>
  );
}

/** Dòng thu chi khác: tài khoản đối ứng, khoản mục, số tiền. */
function CounterLines() {
  const accounts = usePostableAccounts();
  const costItems = useReferenceItems('COST_ITEM');
  return (
    <Form.List
      name="lines"
      rules={[
        {
          validator: async (_, lines?: CashLineForm[]) => {
            if (!lines || lines.length === 0) {
              throw new Error('Nhập ít nhất một dòng');
            }
          },
        },
      ]}
    >
      {(fields, { add, remove }, { errors }) => (
        <>
          {fields.map((field) => (
            <Row gutter={8} key={field.key} style={{ marginTop: 8 }}>
              <Col xs={24} md={7}>
                <Form.Item
                  name={[field.name, 'accountId']}
                  label="Tài khoản đối ứng"
                  rules={[{ required: true, message: 'Chọn tài khoản' }]}
                >
                  <Select showSearch optionFilterProp="label" options={accounts.options} />
                </Form.Item>
              </Col>
              <Col xs={12} md={5}>
                <Form.Item name={[field.name, 'costItemId']} label="Khoản mục">
                  <Select allowClear showSearch optionFilterProp="label" options={costItems.options} />
                </Form.Item>
              </Col>
              <Col xs={12} md={5}>
                <Form.Item
                  name={[field.name, 'amount']}
                  label="Số tiền"
                  rules={[{ required: true, message: 'Nhập số tiền' }]}
                >
                  <InputNumber<number> {...moneyInput} />
                </Form.Item>
              </Col>
              <Col xs={20} md={6}>
                <Form.Item name={[field.name, 'description']} label="Diễn giải">
                  <Input maxLength={500} />
                </Form.Item>
              </Col>
              <Col xs={4} md={1}>
                <Button
                  style={{ marginTop: 30 }}
                  icon={<DeleteOutlined />}
                  onClick={() => remove(field.name)}
                />
              </Col>
            </Row>
          ))}
          <Button
            type="dashed"
            block
            icon={<PlusOutlined />}
            onClick={() => add({})}
            style={{ marginTop: 8 }}
          >
            Thêm dòng
          </Button>
          <Form.ErrorList errors={errors} />
        </>
      )}
    </Form.List>
  );
}
