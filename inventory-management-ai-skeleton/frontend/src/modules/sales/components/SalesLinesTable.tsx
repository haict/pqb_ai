import { Button, Modal, Table, Typography, type TableColumnsType } from 'antd';
import { useState, type ReactNode } from 'react';
import { formatMoney } from '../../accounting/labels';
import { formatNumber } from '../../inventory/labels';
import { useCatalog } from '../../masterdata/lookups';
import type { SalesLine } from '../types';

/** Bảng dòng báo giá / đơn bán; {@code progress}: thêm cột tiến độ, tồn khả dụng (đơn bán). */
export function SalesLinesTable({ lines, progress }: { lines: SalesLine[]; progress?: boolean }) {
  const units = useCatalog('units');
  const base: TableColumnsType<SalesLine> = [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    {
      title: 'Hàng hóa / dịch vụ',
      render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}`,
    },
    {
      title: 'Số lượng',
      width: 110,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${units.nameOf(row.unitId)}`,
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 120, align: 'right', render: formatMoney },
    {
      title: 'CK %',
      dataIndex: 'discountPercent',
      width: 70,
      align: 'right',
      render: (value: number) => formatNumber(value, 2),
    },
    { title: 'Thành tiền', dataIndex: 'amount', width: 130, align: 'right', render: formatMoney },
    { title: 'Tiền thuế', dataIndex: 'taxAmount', width: 110, align: 'right', render: formatMoney },
    { title: 'Giá sàn', dataIndex: 'floorPrice', width: 110, align: 'right', render: formatMoney },
    { title: 'Tặng', dataIndex: 'promotional', width: 55, render: (value: boolean) => (value ? 'Có' : '') },
  ];
  const tracking: TableColumnsType<SalesLine> = [
    {
      title: 'Đã xuất / giao / HĐ / trả',
      width: 170,
      render: (_, row) =>
        row.serviceLine
          ? `Dịch vụ · HĐ ${formatNumber(row.invoicedQuantity)}`
          : [row.issuedQuantity, row.deliveredQuantity, row.invoicedQuantity, row.returnedQuantity]
              .map((value) => formatNumber(value))
              .join(' / '),
    },
    {
      title: 'Còn lại',
      dataIndex: 'outstandingQuantity',
      width: 90,
      align: 'right',
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: 'Khả dụng',
      dataIndex: 'availableQuantity',
      width: 90,
      align: 'right',
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: 'Đang giữ',
      dataIndex: 'reservedQuantity',
      width: 90,
      align: 'right',
      render: (value: number | null) => formatNumber(value),
    },
  ];
  return (
    <>
      <Table<SalesLine>
        rowKey="id"
        size="small"
        pagination={false}
        columns={progress ? [...base, ...tracking] : base}
        dataSource={lines}
        scroll={{ x: progress ? 1600 : 1100 }}
      />
      {progress && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Tiến độ, còn lại, khả dụng, đang giữ tính theo đơn vị cơ sở.
        </Typography.Text>
      )}
    </>
  );
}

/** Nút in: mở hộp xem trước, chỉ in vùng nội dung (BR-SAL-04, BR-SAL-22). */
export function PrintButton({
  label,
  title,
  children,
}: {
  label: string;
  title: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>{label}</Button>
      <Modal
        open={open}
        title={title}
        width={900}
        okText="In"
        cancelText="Đóng"
        onOk={() => window.print()}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <div className="print-area">{children}</div>
      </Modal>
    </>
  );
}
