import { LockOutlined, UnlockOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { toApiError } from '../../../shared/api/apiError';
import { useAuth, useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { formatDateTime } from '../../../shared/format';
import { fiscalPeriodApi, type PeriodLockInput } from '../api';
import { useCompanies, useMyBranches } from '../lookups';
import type { FiscalPeriod, PeriodLockStatus, PeriodModule } from '../types';

const PERIOD_MODULES: { value: PeriodModule; label: string }[] = [
  { value: 'INVENTORY', label: 'Kho' },
  { value: 'DEBT', label: 'Công nợ' },
  { value: 'CASH', label: 'Quỹ' },
  { value: 'ACCOUNTING', label: 'Kế toán' },
];

const LOCK_FIELDS = ['branchIds', 'reason'];

interface LockAction {
  period: FiscalPeriod;
  module: PeriodModule;
  lock: boolean;
}

function periodLabel(period: FiscalPeriod) {
  return `${String(period.month).padStart(2, '0')}/${period.year}`;
}

function moduleLabel(module: PeriodModule) {
  return PERIOD_MODULES.find((item) => item.value === module)?.label ?? module;
}

/** Kỳ kế toán, kỳ kho và khóa/mở kỳ theo chi nhánh × phân hệ (BR-SYS-30..32). */
export function FiscalPeriodsPage() {
  const can = useCan();
  const { workingBranchId } = useAuth();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const companies = useCompanies();
  const branches = useMyBranches();
  const [selectedCompanyId, setCompanyId] = useState<number>();
  const [year, setYear] = useState(dayjs().year());
  const [selectedBranchId, setBranchId] = useState<number>();
  const [action, setAction] = useState<LockAction | null>(null);

  const companyId = selectedCompanyId ?? companies.items[0]?.id;
  const companyBranches = branches.items.filter((branch) => branch.companyId === companyId);
  const branchId =
    selectedBranchId ??
    companyBranches.find((branch) => branch.id === workingBranchId)?.id ??
    companyBranches[0]?.id;
  const branchOptions = companyBranches.map((branch) => ({
    value: branch.id,
    label: `${branch.code} - ${branch.name}`,
  }));

  const query = useQuery({
    queryKey: ['fiscal-periods', companyId, year, branchId],
    queryFn: () => fiscalPeriodApi.list(companyId!, year, branchId!),
    enabled: companyId !== undefined && branchId !== undefined,
  });
  const periods = query.data ?? [];
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['fiscal-periods'] });

  const generate = () =>
    modal.confirm({
      title: `Tạo kỳ tháng năm ${year}?`,
      content: 'Hệ thống tạo 12 kỳ tháng (tháng đã có thì bỏ qua). Kỳ mới ở trạng thái mở.',
      okText: 'Tạo kỳ',
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          const result = await fiscalPeriodApi.generate(companyId!, year, newIdempotencyKey());
          message.success(`Đã tạo ${result.created} kỳ.`);
          await refresh();
        } catch (error) {
          message.error(toApiError(error).message);
        }
      },
    });

  const submitLock = async (values: PeriodLockInput, key: string) => {
    if (!action) {
      return;
    }
    const input = { ...values, module: action.module };
    if (action.lock) {
      await fiscalPeriodApi.lock(action.period.id, input, key);
      message.success(
        `Đã khóa kỳ ${moduleLabel(action.module).toLowerCase()} ${periodLabel(action.period)}.`,
      );
    } else {
      await fiscalPeriodApi.unlock(action.period.id, input, key);
      message.success(
        `Đã mở lại kỳ ${moduleLabel(action.module).toLowerCase()} ${periodLabel(action.period)}.`,
      );
    }
    await refresh();
  };

  const renderLock = (period: FiscalPeriod, status: PeriodLockStatus | undefined, module: PeriodModule) => {
    const locked = status?.locked ?? false;
    const detail = status?.changedAt
      ? `${locked ? 'Khóa' : 'Mở lại'} bởi ${status.changedBy ?? ''} lúc ${formatDateTime(status.changedAt)}${
          status.reason ? ` - ${status.reason}` : ''
        }`
      : undefined;
    const canAct = locked ? can('period:unlock') : can('period:manage');
    return (
      <Space size={4} wrap>
        <Tooltip title={detail}>
          <Tag color={locked ? 'red' : 'green'}>{locked ? 'Đã khóa' : 'Đang mở'}</Tag>
        </Tooltip>
        {canAct && (
          <Button
            size="small"
            icon={locked ? <UnlockOutlined /> : <LockOutlined />}
            onClick={() => setAction({ period, module, lock: !locked })}
          >
            {locked ? 'Mở' : 'Khóa'}
          </Button>
        )}
      </Space>
    );
  };

  const columns: TableColumnsType<FiscalPeriod> = [
    { title: 'Kỳ', width: 90, render: (_, period) => periodLabel(period) },
    {
      title: 'Thời gian',
      width: 200,
      render: (_, period) =>
        `${dayjs(period.startDate).format('DD/MM/YYYY')} - ${dayjs(period.endDate).format('DD/MM/YYYY')}`,
    },
    ...PERIOD_MODULES.map((module) => ({
      title: module.label,
      key: module.value,
      render: (_: unknown, period: FiscalPeriod) =>
        renderLock(
          period,
          period.locks.find((lock) => lock.module === module.value),
          module.value,
        ),
    })),
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Kỳ kế toán, kỳ kho
      </Typography.Title>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <Space wrap>
          {companies.items.length > 1 && (
            <Select
              placeholder="Công ty"
              value={companyId}
              onChange={(value) => {
                setCompanyId(value);
                setBranchId(undefined);
              }}
              options={companies.options}
              style={{ width: 240 }}
            />
          )}
          <InputNumber
            aria-label="Năm"
            min={2000}
            max={2100}
            value={year}
            onChange={(value) => value && setYear(value)}
            style={{ width: 110 }}
          />
          <Select
            aria-label="Chi nhánh"
            placeholder="Chi nhánh"
            value={branchId}
            onChange={setBranchId}
            options={branchOptions}
            style={{ width: 260 }}
          />
        </Space>
        {can('period:manage') && periods.length < 12 && companyId !== undefined && (
          <Button type="primary" onClick={generate}>
            Tạo kỳ năm {year}
          </Button>
        )}
      </div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Khóa kỳ theo thứ tự tháng; kỳ kế toán khóa sau kỳ kho. Mở lại kỳ cần quyền Kế toán trưởng hoặc Giám đốc và phải nhập lý do."
      />
      <Table<FiscalPeriod>
        rowKey="id"
        loading={query.isFetching}
        dataSource={periods}
        columns={columns}
        pagination={false}
        scroll={{ x: 1000 }}
        locale={{ emptyText: <Empty description={`Chưa tạo kỳ năm ${year}`} /> }}
      />

      <FormModal<PeriodLockInput>
        open={action !== null}
        title={
          action
            ? `${action.lock ? 'Khóa' : 'Mở lại'} kỳ ${moduleLabel(action.module).toLowerCase()} ${periodLabel(action.period)}`
            : ''
        }
        okText={action?.lock ? 'Khóa kỳ' : 'Mở lại kỳ'}
        initialValues={{ branchIds: branchId !== undefined ? [branchId] : [] }}
        fieldNames={LOCK_FIELDS}
        onSubmit={submitLock}
        onClose={() => setAction(null)}
      >
        {(form) => (
          <>
            <Form.Item
              name="branchIds"
              label="Chi nhánh"
              rules={[{ required: true, message: 'Chọn ít nhất một chi nhánh' }]}
              extra={
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() =>
                    form.setFieldValue(
                      'branchIds',
                      branchOptions.map((option) => option.value),
                    )
                  }
                >
                  Chọn tất cả chi nhánh
                </Button>
              }
            >
              <Select mode="multiple" options={branchOptions} />
            </Form.Item>
            <Form.Item
              name="reason"
              label="Lý do"
              rules={
                action?.lock ? [] : [{ required: true, whitespace: true, message: 'Nhập lý do mở lại kỳ' }]
              }
            >
              <Input.TextArea rows={3} maxLength={500} />
            </Form.Item>
          </>
        )}
      </FormModal>
    </Card>
  );
}
