import { Table, Typography, type TableColumnsType } from 'antd';
import { formatMoney } from '../../accounting/labels';
import { formatDate, formatNumber } from '../../inventory/labels';
import { useCatalog } from '../../masterdata/lookups';
import type { SalesLine } from '../types';

/**
 * Bảng dòng đơn bán: giá, lô / serial (chỉ định hoặc hàng thực xuất khi đã ghi sổ), số đã trả, giá trị đã giảm; tồn
 * khả dụng và đang giữ khi đơn chưa ghi sổ (đơn vị cơ sở).
 */
export function SalesLinesTable({ lines, posted }: { lines: SalesLine[]; posted: boolean }) {
  const units = useCatalog('units');
  const columns: TableColumnsType<SalesLine> = [
    { title: '#', dataIndex: 'lineNo', width: 45 },
    {
      title: 'Hàng hóa / dịch vụ',
      width: 240,
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
    {
      title: posted ? 'Hàng đã xuất (lô / serial)' : 'Lô / serial chỉ định',
      width: 260,
      render: (_, row) => {
        if (row.serviceLine) {
          return 'Dịch vụ';
        }
        if (posted) {
          return row.items
            .map(
              (item) =>
                [item.lotNo, item.expiryDate ? `HD ${formatDate(item.expiryDate)}` : null, item.serialNo]
                  .filter(Boolean)
                  .join(' · ') + ` (${formatNumber(item.baseQuantity)})`,
            )
            .join('; ');
        }
        return [row.lotNo, ...row.serialNumbers].filter(Boolean).join(', ') || 'FEFO';
      },
    },
    ...(posted
      ? [
          {
            title: 'Đã trả',
            dataIndex: 'returnedQuantity',
            width: 80,
            align: 'right' as const,
            render: (value: number) => formatNumber(value),
          },
          {
            title: 'Đã giảm',
            dataIndex: 'adjustedAmount',
            width: 110,
            align: 'right' as const,
            render: formatMoney,
          },
        ]
      : [
          {
            title: 'Khả dụng',
            dataIndex: 'availableQuantity',
            width: 90,
            align: 'right' as const,
            render: (value: number | null) => formatNumber(value),
          },
          {
            title: 'Đang giữ',
            dataIndex: 'reservedQuantity',
            width: 90,
            align: 'right' as const,
            render: (value: number | null) => formatNumber(value),
          },
        ]),
  ];
  return (
    <>
      <Table<SalesLine>
        rowKey="id"
        size="small"
        pagination={false}
        columns={columns}
        dataSource={lines}
        scroll={{ x: 1600 }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Số lượng hàng đã xuất, đã trả, khả dụng, đang giữ tính theo đơn vị cơ sở.
      </Typography.Text>
    </>
  );
}
