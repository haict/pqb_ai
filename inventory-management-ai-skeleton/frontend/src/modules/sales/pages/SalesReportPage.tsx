import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Card,
  DatePicker,
  Drawer,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/apiError';
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { salesReportApi } from '../api';
import { GROUPINGS, RETURN_TYPES } from '../labels';
import type { SalesReportGrouping, SalesReturnType, SalesSourceDocument, SalesSummaryRow } from '../types';

const SOURCE_LABELS: Record<string, string> = { DBH: 'Đơn bán', ...RETURN_TYPES };

/**
 * Báo cáo doanh số (BR-SAL-51): doanh thu, giảm trừ, doanh thu thuần theo nhân viên / khách / hàng / chi nhánh, công
 * nợ phải thu còn lại; bấm một dòng để xem chứng từ cấu thành (truy ngược số liệu). Lợi nhuận gộp: Giai đoạn 8.
 */
export function SalesReportPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [branchId, setBranchId] = useState<number | undefined>(branches.items[0]?.id);
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs().endOf('month')]);
  const [groupBy, setGroupBy] = useState<SalesReportGrouping>('CUSTOMER');
  const [drill, setDrill] = useState<SalesSummaryRow | null>(null);
  const params = {
    companyId,
    branchId,
    dateFrom: range[0].format('YYYY-MM-DD'),
    dateTo: range[1].format('YYYY-MM-DD'),
    groupBy,
  };
  const summary = useQuery({
    queryKey: ['sales-report', params],
    queryFn: () => salesReportApi.summary(params),
    enabled: companyId > 0,
  });
  const sources = useQuery({
    queryKey: ['sales-report', 'sources', params, drill?.key],
    queryFn: () => salesReportApi.sources({ ...params, key: drill?.key ?? null }),
    enabled: drill !== null,
  });

  const columns: TableColumnsType<SalesSummaryRow> = [
    {
      title: GROUPINGS[groupBy],
      render: (_, row) => (
        <a onClick={() => setDrill(row)}>{[row.code, row.name].filter(Boolean).join(' - ') || '(trống)'}</a>
      ),
    },
    { title: 'Số đơn bán', dataIndex: 'invoiceCount', width: 100, align: 'right' },
    { title: 'Doanh thu', dataIndex: 'revenue', width: 150, align: 'right', render: formatMoney },
    { title: 'Giảm trừ', dataIndex: 'deductions', width: 140, align: 'right', render: formatMoney },
    { title: 'Doanh thu thuần', dataIndex: 'netRevenue', width: 160, align: 'right', render: formatMoney },
    { title: 'Thuế GTGT', dataIndex: 'taxAmount', width: 130, align: 'right', render: formatMoney },
    ...(groupBy === 'PRODUCT'
      ? []
      : [
          {
            title: 'Phải thu còn lại',
            dataIndex: 'receivable',
            width: 150,
            align: 'right' as const,
            render: formatMoney,
          },
        ]),
  ];

  const sourceColumns: TableColumnsType<SalesSourceDocument> = [
    {
      title: 'Loại',
      dataIndex: 'documentType',
      width: 170,
      render: (value: string) => SOURCE_LABELS[value as SalesReturnType] ?? value,
    },
    { title: 'Số chứng từ', dataIndex: 'documentNo', width: 190 },
    { title: 'Ngày hạch toán', dataIndex: 'postingDate', width: 130, render: formatDate },
    { title: 'Giá trị trước thuế', dataIndex: 'amount', align: 'right', render: formatMoney },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Báo cáo doanh số
      </Typography.Title>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="Toàn công ty"
          style={{ width: 220 }}
          value={branchId}
          onChange={setBranchId}
          options={branches.options}
        />
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          value={range}
          allowClear={false}
          onChange={(value) => value && value[0] && value[1] && setRange([value[0], value[1]])}
        />
        <Select
          style={{ width: 220 }}
          value={groupBy}
          onChange={setGroupBy}
          options={(Object.keys(GROUPINGS) as SalesReportGrouping[]).map((value) => ({
            value,
            label: `Theo ${GROUPINGS[value].toLowerCase()}`,
          }))}
        />
      </Space>
      {summary.error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={summary.error instanceof ApiError ? summary.error.message : 'Không tải được báo cáo.'}
        />
      )}
      <Table<SalesSummaryRow>
        rowKey={(row) => String(row.key)}
        size="small"
        loading={summary.isLoading}
        columns={columns}
        dataSource={summary.data?.rows ?? []}
        pagination={false}
        scroll={{ x: 1000 }}
        summary={() =>
          summary.data ? (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0}>
                <strong>Tổng cộng</strong>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1} align="right">
                {summary.data.total.invoiceCount}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={2} align="right">
                {formatMoney(summary.data.total.revenue)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right">
                {formatMoney(summary.data.total.deductions)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={4} align="right">
                <strong>{formatMoney(summary.data.total.netRevenue)}</strong>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={5} align="right">
                {formatMoney(summary.data.total.taxAmount)}
              </Table.Summary.Cell>
              {groupBy !== 'PRODUCT' && (
                <Table.Summary.Cell index={6} align="right">
                  {formatMoney(summary.data.total.receivable)}
                </Table.Summary.Cell>
              )}
            </Table.Summary.Row>
          ) : null
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Số liệu từ đơn bán, trả hàng / giảm giá đã ghi sổ theo ngày hạch toán. Lợi nhuận gộp cần giá vốn (Giai
        đoạn 8).
      </Typography.Text>
      <Drawer
        open={drill !== null}
        width={760}
        title={drill ? `Chứng từ cấu thành: ${[drill.code, drill.name].filter(Boolean).join(' - ')}` : ''}
        onClose={() => setDrill(null)}
      >
        <Table<SalesSourceDocument>
          rowKey={(row) => `${row.documentType}-${row.documentId}`}
          size="small"
          loading={sources.isLoading}
          columns={sourceColumns}
          dataSource={sources.data ?? []}
          pagination={false}
        />
      </Drawer>
    </Card>
  );
}
