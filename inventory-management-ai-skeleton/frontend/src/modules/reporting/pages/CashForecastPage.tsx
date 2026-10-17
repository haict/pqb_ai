import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Form,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/apiError';
import { formatDateTime } from '../../../shared/format';
import { formatDecimal } from '../../../shared/inventorySlip/slipContent';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { cashForecastApi } from '../api';
import {
  GRANULARITIES,
  SOURCE_LABELS,
  cashForecastReport,
  columnLabel,
  forecastItemsReport,
  forecastRows,
  type ForecastRow,
} from '../cashForecast';
import type { CashForecast, ForecastGranularity, ForecastSource } from '../types';
import { BranchCol } from '../../../shared/components/BranchCol';

/** Giới hạn số kỳ của server (ForecastPeriods.Granularity). */
const MAX_PERIODS: Record<ForecastGranularity, number> = { DAY: 92, WEEK: 53, MONTH: 24 };

interface Filters {
  branchId?: number;
  granularity: ForecastGranularity;
  periods: number;
}

interface Cell {
  source: ForecastSource;
  column: CashForecast['columns'][number];
  label: string;
}

/**
 * Dòng tiền dự kiến (BR-RPT-30, BR-CSH-06): số dư quỹ / ngân hàng, dự thu, dự chi theo kỳ từ hôm nay; bấm số tiền của
 * một nguồn xem các khoản cấu thành (BR-RPT-11). In, Excel, PDF.
 */
export function CashForecastPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [form] = Form.useForm<Filters>();
  const granularity = Form.useWatch('granularity', form) ?? 'WEEK';
  const [applied, setApplied] = useState<Filters | null>(null);
  const [cell, setCell] = useState<Cell | null>(null);
  const query = useQuery({
    queryKey: ['cash-forecast', companyId, applied],
    queryFn: () => cashForecastApi.forecast({ companyId, ...(applied as Filters) }),
    enabled: applied !== null,
  });
  const forecast = query.data;
  const report = useMemo(() => (forecast ? cashForecastReport(forecast) : null), [forecast]);
  const items = useQuery({
    queryKey: ['cash-forecast-items', companyId, applied?.branchId, cell],
    queryFn: async () => {
      const target = cell as Cell;
      return forecastItemsReport(
        await cashForecastApi.items({
          companyId,
          branchId: applied?.branchId,
          source: target.source,
          dateFrom: target.column.from ?? undefined,
          dateTo: target.column.to ?? undefined,
        }),
        target.label,
      );
    },
    enabled: cell !== null,
  });

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Dòng tiền dự kiến
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ granularity: 'WEEK', periods: 12 }}
        onFinish={(values) => setApplied(values)}
      >
        <Row gutter={16}>
          <BranchCol xs={24} md={10}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn bộ phạm vi được xem" options={branches.options} />
            </Form.Item>
          </BranchCol>
          <Col xs={12} md={6}>
            <Form.Item name="granularity" label="Chia kỳ theo">
              <Select
                options={(Object.keys(GRANULARITIES) as ForecastGranularity[]).map((value) => ({
                  value,
                  label: GRANULARITIES[value],
                }))}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={4}>
            <Form.Item
              name="periods"
              label="Số kỳ"
              rules={[
                { required: true, message: 'Nhập số kỳ' },
                {
                  type: 'number',
                  min: 1,
                  max: MAX_PERIODS[granularity],
                  message: `Từ 1 đến ${MAX_PERIODS[granularity]}`,
                },
              ]}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
            Xem báo cáo
          </Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      {query.error ? (
        <Alert
          type="error"
          showIcon
          message={query.error instanceof ApiError ? query.error.message : 'Không tải được báo cáo.'}
        />
      ) : query.isFetching ? (
        <Spin />
      ) : forecast ? (
        <ForecastTable forecast={forecast} onOpen={setCell} />
      ) : (
        <Typography.Text type="secondary">Chọn điều kiện rồi bấm Xem báo cáo.</Typography.Text>
      )}
      <Drawer
        open={cell !== null}
        onClose={() => setCell(null)}
        width="min(1100px, 100vw)"
        title="Khoản cấu thành"
        extra={items.data && <ReportActions report={items.data} />}
      >
        <ReportResult loading={items.isFetching} error={items.error} report={items.data ?? null} />
      </Drawer>
    </Card>
  );
}

function ForecastTable({ forecast, onOpen }: { forecast: CashForecast; onOpen: (cell: Cell) => void }) {
  const money = (value: number | null) => (value === null ? '' : formatDecimal(value, 0));
  return (
    <>
      <Table<ForecastRow>
        size="small"
        bordered
        rowKey="key"
        pagination={false}
        scroll={{ x: 'max-content' }}
        dataSource={forecastRows(forecast)}
        onRow={(row) => ({ style: row.bold ? { fontWeight: 600 } : undefined })}
        columns={[
          { title: 'Chỉ tiêu', dataIndex: 'label', fixed: 'left', width: 260 },
          ...forecast.columns.map((column, index) => ({
            key: column.key,
            title: columnLabel(forecast.granularity, column),
            align: 'right' as const,
            render: (_: unknown, row: ForecastRow) => {
              const value = row.values[index] ?? 0;
              const source = row.source;
              if (!source || value === 0) {
                return money(value);
              }
              return (
                <Typography.Link
                  onClick={() =>
                    onOpen({
                      source,
                      column,
                      label: `${SOURCE_LABELS[source]} - ${columnLabel(forecast.granularity, column)}`,
                    })
                  }
                >
                  {money(value)}
                </Typography.Link>
              );
            },
          })),
          { title: 'Cộng', align: 'right', render: (_, row) => money(row.total) },
        ]}
      />
      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        {forecast.cashBalanceIncluded
          ? `Số dư đầu kỳ: ${forecast.cashAccounts
              .map((account) => `${account.code} ${money(account.balance)}`)
              .join(' · ')}. `
          : 'Không gồm số dư quỹ, ngân hàng (chỉ dữ liệu của mình). '}
        Quá hạn: ngày dự kiến trước hôm nay. Bấm số tiền để xem khoản cấu thành. Số liệu lấy lúc{' '}
        {formatDateTime(forecast.generatedAt)}.
      </Typography.Paragraph>
    </>
  );
}
