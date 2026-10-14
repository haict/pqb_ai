import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Drawer, Form, Row, Select, Space, Spin, Statistic, Table, Typography } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/apiError';
import { useCan } from '../../../shared/auth/authContext';
import { formatDateTime } from '../../../shared/format';
import { formatDecimal } from '../../../shared/inventorySlip/slipContent';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import type { TableReport } from '../../../shared/report/tableReport';
import { displayDate, initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { cashForecastApi, dashboardApi } from '../api';
import {
  backlogDetailsReport,
  debtDetailsReport,
  expiryDetailsReport,
  tradeDetailsReport,
} from '../dashboard';
import type { CashForecast, DashboardDebt, DashboardRanked, TradeKind } from '../types';

const TOP_OPTIONS = [5, 10, 20].map((value) => ({ value, label: `Top ${value}` }));

interface Filters extends PeriodValues {
  branchId?: number;
  top: number;
}

interface Params {
  companyId: number;
  branchId?: number;
  dateFrom: string;
  dateTo: string;
  top: number;
}

/** Chỉ tiêu đang xem chi tiết; dòng top lọc thêm theo hàng hoặc đối tượng. */
type Detail =
  | { metric: TradeKind; partnerId?: number; productId?: number; label?: string }
  | { metric: 'receivables' | 'payables' | 'expiring' | 'purchase-backlog' | 'sales-backlog' };

function money(value: number) {
  return formatDecimal(value, 0);
}

function toParams(companyId: number, values: Filters): Params {
  return { companyId, branchId: values.branchId, top: values.top, ...rangeParams(values.range) };
}

async function loadDetail(detail: Detail, params: Params): Promise<TableReport> {
  const scope = { companyId: params.companyId, branchId: params.branchId };
  switch (detail.metric) {
    case 'revenue':
    case 'purchases':
      return tradeDetailsReport(
        await dashboardApi.trade(detail.metric, {
          ...scope,
          dateFrom: params.dateFrom,
          dateTo: params.dateTo,
          partnerId: detail.partnerId,
          productId: detail.productId,
        }),
        detail.label,
      );
    case 'receivables':
    case 'payables':
      return debtDetailsReport(await dashboardApi.debts(detail.metric, scope));
    case 'expiring':
      return expiryDetailsReport(await dashboardApi.expiring(scope));
    case 'purchase-backlog':
    case 'sales-backlog':
      return backlogDetailsReport(await dashboardApi.backlog(detail.metric, scope));
  }
}

/** Thẻ chỉ tiêu bấm được để xem danh sách chi tiết (BR-RPT-11). */
function MetricCard({
  title,
  value,
  extra,
  onOpen,
}: {
  title: string;
  value: string;
  extra?: ReactNode;
  onOpen: () => void;
}) {
  return (
    <Card size="small" hoverable onClick={onOpen} style={{ height: '100%' }}>
      <Statistic title={title} value={value} />
      {extra && <Typography.Text type="secondary">{extra}</Typography.Text>}
    </Card>
  );
}

function debtExtra(debt: DashboardDebt) {
  return (
    <>
      Quá hạn {money(debt.overdueAmount)} ({debt.overdueCount} khoản)
      {debt.overdueBuckets.map((bucket) => (
        <div key={bucket.fromDays}>
          {bucket.toDays === null ? `Trên ${bucket.fromDays - 1}` : `${bucket.fromDays}–${bucket.toDays}`}{' '}
          ngày: {money(bucket.amount)}
        </div>
      ))}
    </>
  );
}

/** Số kỳ tuần của thẻ dòng tiền dự kiến trên dashboard. */
const FORECAST_WEEKS = 4;

/** Thẻ dòng tiền dự kiến (BR-RPT-09): tổng đến hết kỳ tuần thứ {@link FORECAST_WEEKS}, gồm khoản quá hạn. */
function forecastExtra(forecast: CashForecast) {
  const upTo = FORECAST_WEEKS + 1;
  const inflow = forecast.inflow.slice(0, upTo).reduce((total, value) => total + value, 0);
  const outflow = forecast.outflow.slice(0, upTo).reduce((total, value) => total + value, 0);
  return (
    <>
      {forecast.cashBalanceIncluded
        ? `Số dư hiện tại ${money(forecast.openingBalance)}`
        : 'Không gồm số dư quỹ'}
      <br />
      Dự thu {money(inflow)} · dự chi {money(outflow)} (gồm quá hạn)
    </>
  );
}

function TopTable({
  title,
  rows,
  onOpen,
}: {
  title: string;
  rows: DashboardRanked[];
  onOpen: (row: DashboardRanked) => void;
}) {
  return (
    <Card size="small" title={title} style={{ height: '100%' }}>
      <Table<DashboardRanked>
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={rows}
        locale={{ emptyText: 'Không có phát sinh' }}
        onRow={(row) => ({ onClick: () => onOpen(row), style: { cursor: 'pointer' } })}
        columns={[
          { title: '#', width: 36, render: (_, __, index) => index + 1 },
          { title: 'Tên', render: (_, row) => `${row.code} - ${row.name}` },
          { title: 'Giá trị', align: 'right', render: (_, row) => money(row.amount) },
        ]}
      />
    </Card>
  );
}

/**
 * Dashboard điều hành trên trang chủ (BR-RPT-01, 02, 04..06, 10..13): doanh thu, giá trị mua, công nợ, hàng sắp hết hạn,
 * đơn chưa giao đủ, top; bấm chỉ tiêu xem chứng từ nguồn (In, Excel, PDF). Số liệu theo phạm vi người xem.
 */
export function DashboardPanel() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [form] = Form.useForm<Filters>();
  const initial = useMemo<Filters>(() => ({ ...initialPeriod('THIS_MONTH'), top: 10 }), []);
  const [params, setParams] = useState<Params>(() => toParams(companyId, initial));
  const [detail, setDetail] = useState<Detail | null>(null);
  const summary = useQuery({ queryKey: ['dashboard', params], queryFn: () => dashboardApi.summary(params) });
  const details = useQuery({
    queryKey: ['dashboard-detail', params, detail],
    queryFn: () => loadDetail(detail as Detail, params),
    enabled: detail !== null,
  });
  const data = summary.data;
  const can = useCan();
  const navigate = useNavigate();
  const forecast = useQuery({
    queryKey: ['dashboard-forecast', params.companyId, params.branchId],
    queryFn: () =>
      cashForecastApi.forecast({
        companyId: params.companyId,
        branchId: params.branchId,
        granularity: 'WEEK',
        periods: FORECAST_WEEKS,
      }),
    enabled: can('cash-forecast:view'),
  });
  const backlogSpan = forecast.data ? 6 : 8;

  return (
    <Card
      style={{ marginBottom: 16 }}
      title="Dashboard điều hành"
      extra={
        data && (
          <Typography.Text type="secondary">
            Số liệu lấy lúc {formatDateTime(data.generatedAt)}
          </Typography.Text>
        )
      }
    >
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={initial}
        onValuesChange={(_, values) => {
          if (values.range) {
            setParams(toParams(companyId, values));
          }
        }}
      >
        <Row gutter={16}>
          <PeriodFields />
          <Col xs={24} md={6}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn bộ phạm vi được xem" options={branches.options} />
            </Form.Item>
          </Col>
          <Col xs={24} md={4}>
            <Form.Item name="top" label="Số dòng top">
              <Select options={TOP_OPTIONS} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
      {summary.error ? (
        <Alert
          type="error"
          showIcon
          message={summary.error instanceof ApiError ? summary.error.message : 'Không tải được dashboard.'}
        />
      ) : !data ? (
        <Spin />
      ) : (
        <Spin spinning={summary.isFetching}>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12} xl={6}>
              <MetricCard
                title="Doanh thu thuần"
                value={money(data.revenue.netAmount)}
                extra={
                  <>
                    Bán {money(data.revenue.grossAmount)} − giảm trừ {money(data.revenue.deductionAmount)} (
                    {data.revenue.documentCount} đơn)
                    <br />
                    Giá vốn, lợi nhuận: có từ Giai đoạn 8
                  </>
                }
                onOpen={() => setDetail({ metric: 'revenue' })}
              />
            </Col>
            <Col xs={24} md={12} xl={6}>
              <MetricCard
                title="Giá trị mua thuần"
                value={money(data.purchases.netAmount)}
                extra={`Mua ${money(data.purchases.grossAmount)} − trả lại / giảm giá ${money(data.purchases.deductionAmount)}`}
                onOpen={() => setDetail({ metric: 'purchases' })}
              />
            </Col>
            <Col xs={24} md={12} xl={6}>
              <MetricCard
                title="Phải thu còn lại"
                value={money(data.receivables.totalAmount)}
                extra={debtExtra(data.receivables)}
                onOpen={() => setDetail({ metric: 'receivables' })}
              />
            </Col>
            <Col xs={24} md={12} xl={6}>
              <MetricCard
                title="Phải trả còn lại"
                value={money(data.payables.totalAmount)}
                extra={debtExtra(data.payables)}
                onOpen={() => setDetail({ metric: 'payables' })}
              />
            </Col>
            <Col xs={24} md={12} xl={backlogSpan}>
              <MetricCard
                title="Hàng đã / sắp hết hạn (lô × kho)"
                value={`${data.expiry.expiredCount} / ${data.expiry.expiringCount}`}
                extra={`Hết hạn trước ${displayDate(data.expiry.warningUntil)} (cảnh báo ${data.expiry.warningDays} ngày)`}
                onOpen={() => setDetail({ metric: 'expiring' })}
              />
            </Col>
            <Col xs={24} md={12} xl={backlogSpan}>
              <MetricCard
                title="Đơn mua chưa nhận"
                value={`${data.purchaseBacklog.orderCount} đơn`}
                extra={`${money(data.purchaseBacklog.totalAmount)} · trễ hẹn ${data.purchaseBacklog.overdueCount} đơn`}
                onOpen={() => setDetail({ metric: 'purchase-backlog' })}
              />
            </Col>
            <Col xs={24} md={12} xl={backlogSpan}>
              <MetricCard
                title="Đơn bán chưa giao đủ"
                value={`${data.salesBacklog.orderCount} đơn`}
                extra={`${money(data.salesBacklog.totalAmount)} · trễ hẹn ${data.salesBacklog.overdueCount} đơn`}
                onOpen={() => setDetail({ metric: 'sales-backlog' })}
              />
            </Col>
            {forecast.data && (
              <Col xs={24} md={12} xl={6}>
                <MetricCard
                  title={`Số dư dự kiến sau ${FORECAST_WEEKS} tuần`}
                  value={money(forecast.data.closing[FORECAST_WEEKS] ?? forecast.data.openingBalance)}
                  extra={forecastExtra(forecast.data)}
                  onOpen={() => navigate('/reports/cash-flow-forecast')}
                />
              </Col>
            )}
            <Col xs={24} xl={8}>
              <TopTable
                title="Top hàng hóa theo doanh thu"
                rows={data.topProducts}
                onOpen={(row) =>
                  setDetail({ metric: 'revenue', productId: row.id, label: `Hàng ${row.code} - ${row.name}` })
                }
              />
            </Col>
            <Col xs={24} xl={8}>
              <TopTable
                title="Top khách hàng theo doanh thu"
                rows={data.topCustomers}
                onOpen={(row) =>
                  setDetail({
                    metric: 'revenue',
                    partnerId: row.id,
                    label: `Khách ${row.code} - ${row.name}`,
                  })
                }
              />
            </Col>
            <Col xs={24} xl={8}>
              <TopTable
                title="Top nhà cung cấp theo giá trị mua"
                rows={data.topSuppliers}
                onOpen={(row) =>
                  setDetail({
                    metric: 'purchases',
                    partnerId: row.id,
                    label: `NCC ${row.code} - ${row.name}`,
                  })
                }
              />
            </Col>
          </Row>
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            Doanh thu, giá trị mua theo chứng từ đã ghi sổ trong kỳ, chưa thuế, sau chiết khấu. Công nợ, hạn
            dùng, đơn chưa giao tính đến ngày {displayDate(data.today)}. Bấm vào chỉ tiêu để xem chứng từ
            nguồn.
          </Typography.Paragraph>
        </Spin>
      )}
      <Drawer
        open={detail !== null}
        onClose={() => setDetail(null)}
        width="min(1100px, 100vw)"
        title="Chi tiết chỉ tiêu"
        extra={
          details.data && (
            <Space>
              <ReportActions report={details.data} />
            </Space>
          )
        }
      >
        <ReportResult loading={details.isFetching} error={details.error} report={details.data ?? null} />
      </Drawer>
    </Card>
  );
}
