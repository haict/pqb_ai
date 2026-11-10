import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Card,
  DatePicker,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { inventoryReportApi } from '../../reporting/api';
import { stockApi } from '../api';
import { IssuedOrdersModal, type IssuedOrdersTarget } from '../components/IssuedOrdersModal';
import { TRANSACTION_TYPES, formatDate, formatNumber } from '../labels';
import { ItemConditionTag, QualityTag } from '../components/StatusTags';
import type { InventoryTransaction, ItemCondition, StockAlert, StockBalance, StockSummary } from '../types';
import { DATE_INPUT_FORMAT } from '../../../shared/dateInput';

interface Filters {
  warehouseId?: number;
  productId?: number;
}

function SummaryTab({ filters }: { filters: Filters }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const units = useCatalog('units');
  const can = useCan();
  const { tableProps } = useServerTable('stock-summaries', stockApi.summaries, { companyId, ...filters });
  // D-68: xuất bán trong kỳ theo đơn đã / chưa có số hóa đơn; chỉ người xem được báo cáo kho.
  const showInvoice = can('inventory-report:view');
  const [period, setPeriod] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf('month'), dayjs()]);
  const [target, setTarget] = useState<IssuedOrdersTarget | null>(null);
  const dateFrom = period[0].format('YYYY-MM-DD');
  const dateTo = period[1].format('YYYY-MM-DD');
  const productIds = [...new Set((tableProps.dataSource ?? []).map((row) => row.productId))];
  const issued = useQuery({
    queryKey: ['issued-invoice-status', companyId, filters.warehouseId, productIds, dateFrom, dateTo],
    queryFn: () =>
      inventoryReportApi.issuedInvoiceStatus({
        companyId,
        warehouseId: filters.warehouseId,
        productIds,
        dateFrom,
        dateTo,
      }),
    enabled: showInvoice && productIds.length > 0,
  });
  const issuedByKey = new Map(
    (issued.data?.rows ?? []).map((row) => [`${row.productId}-${row.warehouseId}`, row]),
  );
  const issuedColumn = (title: string, invoiced: boolean): TableColumnsType<StockSummary>[number] => ({
    title,
    align: 'right',
    width: 130,
    render: (_, row) => {
      const found = issuedByKey.get(`${row.productId}-${row.warehouseId}`);
      const quantity = found ? (invoiced ? found.invoicedQuantity : found.uninvoicedQuantity) : 0;
      if (!quantity) {
        return formatNumber(0);
      }
      return (
        <Button
          type="link"
          size="small"
          style={{ padding: 0 }}
          onClick={() =>
            setTarget({
              productId: row.productId,
              productLabel: `${row.productCode ?? ''} - ${row.productName ?? ''}`,
              warehouseId: row.warehouseId,
              invoiced,
              dateFrom,
              dateTo,
            })
          }
        >
          {formatNumber(quantity)}
        </Button>
      );
    },
  });
  const columns: TableColumnsType<StockSummary> = [
    { title: 'Mã hàng', dataIndex: 'productCode', width: 130 },
    { title: 'Tên hàng', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'baseUnitId', width: 80, render: (id: number | null) => units.nameOf(id) },
    { title: 'Kho', dataIndex: 'warehouseId', width: 110, render: (id: number) => warehouses.codeOf(id) },
    {
      title: 'Tồn thực tế',
      dataIndex: 'onHandQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    {
      title: 'Bán được',
      dataIndex: 'sellableQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    {
      title: 'Giữ chỗ',
      dataIndex: 'reservedQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    {
      title: 'Khả dụng',
      dataIndex: 'availableQuantity',
      align: 'right',
      render: (v: number) => <strong>{formatNumber(v)}</strong>,
    },
    {
      title: 'Đang về',
      dataIndex: 'incomingQuantity',
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    ...(showInvoice ? [issuedColumn('Đã có HĐ', true), issuedColumn('Chưa có HĐ', false)] : []),
  ];
  return (
    <>
      {showInvoice && (
        <Space style={{ marginBottom: 12 }}>
          <Typography.Text>Kỳ xuất bán (theo ngày hạch toán):</Typography.Text>
          <DatePicker.RangePicker
            format={DATE_INPUT_FORMAT}
            allowClear={false}
            value={period}
            onChange={(range) => {
              if (range?.[0] && range[1]) {
                setPeriod([range[0], range[1]]);
              }
            }}
          />
        </Space>
      )}
      <Table<StockSummary>
        {...tableProps}
        rowKey={(row) => `${row.productId}-${row.warehouseId}`}
        columns={columns}
        scroll={{ x: showInvoice ? 1260 : 1000 }}
      />
      {target && <IssuedOrdersModal target={target} onClose={() => setTarget(null)} />}
    </>
  );
}

function BalanceTab({ filters }: { filters: Filters }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [qualityStatus, setQualityStatus] = useState<QualityStatus>();
  const [itemCondition, setItemCondition] = useState<ItemCondition>();
  const { tableProps } = useServerTable('stock-balances', stockApi.balances, {
    companyId,
    qualityStatus,
    itemCondition,
    ...filters,
  });
  const columns: TableColumnsType<StockBalance> = [
    { title: 'Mã hàng', dataIndex: 'productCode', width: 130 },
    { title: 'Tên hàng', dataIndex: 'productName' },
    { title: 'Kho', dataIndex: 'warehouseId', width: 110, render: (id: number) => warehouses.codeOf(id) },
    {
      title: 'Lô',
      dataIndex: 'lotNo',
      render: (lotNo: string | null, row) => (
        <Space size={4}>
          {lotNo}
          {row.lotBlocked && <Tag color="red">Khóa</Tag>}
        </Space>
      ),
    },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', width: 110, render: formatDate },
    { title: 'Serial', dataIndex: 'serialNo' },
    {
      title: 'Máy cũ',
      dataIndex: 'itemCondition',
      width: 90,
      render: (condition: ItemCondition | null) => <ItemConditionTag condition={condition} />,
    },
    {
      title: 'Trạng thái CL',
      dataIndex: 'qualityStatus',
      render: (status: QualityStatus) => <QualityTag status={status} />,
    },
    { title: 'Tồn', dataIndex: 'onHandQuantity', align: 'right', render: (v: number) => formatNumber(v) },
  ];
  return (
    <>
      <Select
        allowClear
        placeholder="Trạng thái chất lượng"
        style={{ width: 220, marginBottom: 12 }}
        value={qualityStatus}
        onChange={setQualityStatus}
        options={(Object.keys(QUALITY_STATUSES) as QualityStatus[]).map((value) => ({
          value,
          label: QUALITY_STATUSES[value].label,
        }))}
      />
      <Select
        allowClear
        placeholder="Máy mới / cũ"
        style={{ width: 160, marginBottom: 12, marginLeft: 8 }}
        value={itemCondition}
        onChange={setItemCondition}
        options={[
          { value: 'USED', label: 'Máy cũ' },
          { value: 'NEW', label: 'Mới' },
        ]}
      />
      <Table<StockBalance> {...tableProps} columns={columns} scroll={{ x: 1000 }} />
    </>
  );
}

function LedgerTab({ filters }: { filters: Filters }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const { tableProps } = useServerTable('inventory-transactions', stockApi.transactions, {
    companyId,
    dateFrom: range?.[0]?.format('YYYY-MM-DD'),
    dateTo: range?.[1]?.format('YYYY-MM-DD'),
    ...filters,
  });
  const columns: TableColumnsType<InventoryTransaction> = [
    { title: 'Ngày HT', dataIndex: 'postingDate', width: 100, sorter: true, render: formatDate },
    {
      title: 'Chứng từ',
      dataIndex: 'referenceNo',
      render: (no: string, row) =>
        /^P(NK|XK|XH|CK|DC|CL|DK)$/.test(row.referenceType) ? (
          <Link to={`/inventory/documents/${row.referenceId}`}>{no}</Link>
        ) : (
          no
        ),
    },
    {
      title: 'Nghiệp vụ',
      dataIndex: 'transactionType',
      render: (v: InventoryTransaction['transactionType']) => TRANSACTION_TYPES[v],
    },
    { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.codeOf(id) },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Serial', dataIndex: 'serialNo' },
    {
      title: 'Máy cũ',
      dataIndex: 'itemCondition',
      width: 90,
      render: (condition: ItemCondition | null) => <ItemConditionTag condition={condition} />,
    },
    {
      title: 'Nhập / xuất',
      align: 'right',
      render: (_, row) => `${row.direction === 'IN' ? '+' : '-'}${formatNumber(row.quantity)}`,
    },
  ];
  return (
    <>
      <DatePicker.RangePicker
        format={DATE_INPUT_FORMAT}
        value={range}
        onChange={setRange}
        style={{ marginBottom: 12 }}
      />
      <Table<InventoryTransaction> {...tableProps} columns={columns} scroll={{ x: 1000 }} />
    </>
  );
}

function AlertTab({ warehouseId }: { warehouseId?: number }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const query = useQuery({
    queryKey: ['stock-alerts', companyId, warehouseId],
    queryFn: () => stockApi.alerts(companyId, warehouseId),
    enabled: companyId > 0,
  });
  const columns: TableColumnsType<StockAlert> = [
    {
      title: 'Cảnh báo',
      dataIndex: 'alertType',
      width: 150,
      render: (type: StockAlert['alertType'], row) =>
        type === 'BELOW_MIN' ? (
          <Tag color="orange">Dưới định mức</Tag>
        ) : (row.daysToExpiry ?? 0) < 0 ? (
          <Tag color="red">Đã hết hạn</Tag>
        ) : (
          <Tag color="gold">Sắp hết hạn</Tag>
        ),
    },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.codeOf(id) },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', render: formatDate },
    { title: 'Còn (ngày)', dataIndex: 'daysToExpiry', align: 'right' },
    { title: 'Tồn', dataIndex: 'onHandQuantity', align: 'right', render: (v: number) => formatNumber(v) },
    {
      title: 'Định mức tối thiểu',
      dataIndex: 'minQuantity',
      align: 'right',
      render: (v: number | null) => formatNumber(v),
    },
  ];
  return (
    <Table<StockAlert>
      rowKey={(row) => `${row.alertType}-${row.productId}-${row.warehouseId}-${row.lotId ?? 0}`}
      loading={query.isFetching}
      dataSource={query.data ?? []}
      columns={columns}
      pagination={{ pageSize: 50 }}
      scroll={{ x: 900 }}
    />
  );
}

/** Tồn kho: bốn chỉ tiêu tồn theo hàng / kho, tồn chi tiết, sổ kho, cảnh báo (BR mục 5.4, BR-INV-40). */
export function StockPage() {
  const warehouses = useWarehouses();
  const [filters, setFilters] = useState<Filters>({});
  return (
    <Card>
      <Typography.Title level={4}>Tồn kho</Typography.Title>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Kho"
          style={{ width: 220 }}
          value={filters.warehouseId}
          onChange={(warehouseId) => setFilters((current) => ({ ...current, warehouseId }))}
          options={warehouses.options}
        />
        <ProductSelect
          allowClear
          style={{ width: 320 }}
          value={filters.productId}
          onChange={(productId) => setFilters((current) => ({ ...current, productId }))}
        />
      </Space>
      <Tabs
        items={[
          { key: 'summary', label: 'Tồn theo hàng, kho', children: <SummaryTab filters={filters} /> },
          { key: 'balance', label: 'Tồn chi tiết', children: <BalanceTab filters={filters} /> },
          { key: 'ledger', label: 'Sổ kho', children: <LedgerTab filters={filters} /> },
          { key: 'alerts', label: 'Cảnh báo', children: <AlertTab warehouseId={filters.warehouseId} /> },
        ]}
      />
    </Card>
  );
}
