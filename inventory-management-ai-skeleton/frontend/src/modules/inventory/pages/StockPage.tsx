import { useQuery } from '@tanstack/react-query';
import { Card, DatePicker, Select, Space, Table, Tabs, Tag, Typography, type TableColumnsType } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { stockApi } from '../api';
import { TRANSACTION_TYPES, formatDate, formatNumber } from '../labels';
import { QualityTag } from '../components/StatusTags';
import type { InventoryTransaction, StockAlert, StockBalance, StockSummary } from '../types';

interface Filters {
  warehouseId?: number;
  productId?: number;
}

function SummaryTab({ filters }: { filters: Filters }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const units = useCatalog('units');
  const { tableProps } = useServerTable('stock-summaries', stockApi.summaries, { companyId, ...filters });
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
  ];
  return (
    <Table<StockSummary>
      {...tableProps}
      rowKey={(row) => `${row.productId}-${row.warehouseId}`}
      columns={columns}
      scroll={{ x: 1000 }}
    />
  );
}

function BalanceTab({ filters }: { filters: Filters }) {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [qualityStatus, setQualityStatus] = useState<QualityStatus>();
  const { tableProps } = useServerTable('stock-balances', stockApi.balances, {
    companyId,
    qualityStatus,
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
      title: 'Nhập / xuất',
      align: 'right',
      render: (_, row) => `${row.direction === 'IN' ? '+' : '-'}${formatNumber(row.quantity)}`,
    },
  ];
  return (
    <>
      <DatePicker.RangePicker
        format="DD/MM/YYYY"
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
