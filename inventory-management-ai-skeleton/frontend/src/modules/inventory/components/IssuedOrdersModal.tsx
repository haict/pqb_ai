import { useQuery } from '@tanstack/react-query';
import { Alert, Modal, Table, type TableColumnsType } from 'antd';
import { Link } from 'react-router-dom';
import { useCompanyId } from '../../masterdata/lookups';
import { inventoryReportApi } from '../../reporting/api';
import type { IssuedInvoiceOrders } from '../../reporting/types';
import { formatDate, formatNumber } from '../labels';

export interface IssuedOrdersTarget {
  productId: number;
  productLabel: string;
  warehouseId: number;
  invoiced: boolean;
  dateFrom: string;
  dateTo: string;
}

type Order = IssuedInvoiceOrders['orders'][number];

/** Đơn bán cấu thành số xuất đã / chưa có hóa đơn của một hàng tại một kho (D-68, truy ngược số liệu). */
export function IssuedOrdersModal({ target, onClose }: { target: IssuedOrdersTarget; onClose(): void }) {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['issued-invoice-orders', companyId, target],
    queryFn: () => inventoryReportApi.issuedInvoiceOrders({ companyId, ...target }),
  });
  const columns: TableColumnsType<Order> = [
    {
      title: 'Đơn bán',
      dataIndex: 'documentNo',
      width: 150,
      render: (value: string, row) => <Link to={`/sales/orders?id=${row.orderId}`}>{value}</Link>,
    },
    { title: 'Ngày HT', dataIndex: 'postingDate', width: 100, render: formatDate },
    {
      title: 'Khách hàng',
      render: (_, row) => [row.customerCode, row.customerName].filter(Boolean).join(' - '),
    },
    ...(target.invoiced
      ? [
          {
            title: 'Hóa đơn',
            width: 200,
            render: (_: unknown, row: Order) =>
              `${row.einvoiceSeries ?? ''}-${row.einvoiceNo ?? ''} ngày ${formatDate(row.einvoiceDate)}`,
          },
        ]
      : []),
    {
      title: 'SL xuất',
      dataIndex: 'quantity',
      width: 100,
      align: 'right',
      render: (value: number) => formatNumber(value),
    },
  ];
  return (
    <Modal
      open
      width={820}
      footer={null}
      onCancel={onClose}
      title={`${target.invoiced ? 'Đã có hóa đơn' : 'Chưa có hóa đơn'} - ${target.productLabel} (${formatDate(
        target.dateFrom,
      )} - ${formatDate(target.dateTo)})`}
    >
      {query.data?.truncated && (
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Chỉ hiện 1.000 đơn đầu tiên." />
      )}
      <Table<Order>
        rowKey="orderId"
        size="small"
        loading={query.isFetching}
        dataSource={query.data?.orders ?? []}
        columns={columns}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 700 }}
      />
    </Modal>
  );
}
