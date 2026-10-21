import { useQuery } from '@tanstack/react-query';
import { Alert, Input, InputNumber, Modal, Table, Typography, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { formatDate } from '../../inventory/labels';
import { formatAmount, formatQuantity } from '../../../shared/format';
import { salesOrderApi } from '../api';
import type { ReceiptSource } from '../types';
import type { ReceiptPick } from './salesLines';

interface Props {
  branchId: number;
  warehouseId: number;
  productId: number;
  productLabel?: string;
  /** Tên đơn vị cơ sở (số lượng trong danh sách theo đơn vị cơ sở). */
  baseUnitName: string;
  onCancel(): void;
  onPick(picks: ReceiptPick[], lotOverrideReason: string | undefined): void;
}

/**
 * Hộp chọn chứng từ nhập (F8, xuất đích danh như MISA, D-63): dòng nhập theo lô / serial còn tồn tại kho của đơn, xếp
 * FEFO. Chọn lô khác lô hết hạn sớm nhất phải nhập lý do (BR-INV-21). Tồn hiện có là tồn của lô / serial tại kho; lô
 * nhập nhiều lần dùng chung tồn nên tổng lấy theo lô không vượt tồn đó.
 */
export function ReceiptSourcePicker(props: Props) {
  const { branchId, warehouseId, productId, productLabel, baseUnitName, onCancel, onPick } = props;
  const query = useQuery({
    queryKey: ['receipt-sources', branchId, warehouseId, productId],
    queryFn: () => salesOrderApi.receiptSources({ branchId, warehouseId, productId }),
  });
  const rows = query.data ?? [];
  const [selected, setSelected] = useState<number[]>([]);
  const [quantities, setQuantities] = useState<Record<number, number | null>>({});
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const chosen = rows.filter((row) => selected.includes(row.transactionId));
  const quantityOf = (row: ReceiptSource) =>
    row.serialNo ? 1 : (quantities[row.transactionId] ?? row.availableQuantity);
  const fefoLot = rows.find((row) => row.lotNo)?.lotNo ?? null;
  const needsReason = chosen.some((row) => row.lotNo && row.lotNo !== fefoLot);

  const confirm = () => {
    if (chosen.length === 0) {
      setError('Chọn ít nhất một chứng từ nhập.');
      return;
    }
    const byLot = new Map<string, number>();
    for (const row of chosen) {
      const quantity = quantityOf(row);
      if (!quantity || quantity <= 0) {
        setError(`Nhập số lượng lấy cho ${row.documentNo}.`);
        return;
      }
      const lotKey = row.serialNo ?? row.lotNo ?? String(row.transactionId);
      const total = (byLot.get(lotKey) ?? 0) + quantity;
      if (total > row.availableQuantity) {
        setError(`Lô / serial ${lotKey} chỉ còn ${formatQuantity(row.availableQuantity)} ${baseUnitName}.`);
        return;
      }
      byLot.set(lotKey, total);
    }
    if (needsReason && !reason.trim()) {
      setError('Nhập lý do chọn lô khác lô hết hạn sớm nhất (BR-INV-21).');
      return;
    }
    onPick(
      chosen.map((row) => ({ source: row, quantity: quantityOf(row) })),
      needsReason ? reason.trim() : undefined,
    );
  };

  const columns: TableColumnsType<ReceiptSource> = [
    { title: 'Số chứng từ', dataIndex: 'documentNo', width: 170 },
    { title: 'Ngày nhập', dataIndex: 'postingDate', width: 100, render: formatDate },
    { title: 'Lô', dataIndex: 'lotNo', width: 120 },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', width: 100, render: formatDate },
    { title: 'Serial', dataIndex: 'serialNo', width: 140 },
    { title: 'SL nhập', dataIndex: 'receivedQuantity', align: 'right', width: 90, render: formatQuantity },
    { title: 'Đơn giá nhập', dataIndex: 'unitCost', align: 'right', width: 110, render: formatAmount },
    {
      title: 'Tồn hiện có',
      dataIndex: 'availableQuantity',
      align: 'right',
      width: 100,
      render: formatQuantity,
    },
    {
      title: 'SL lấy',
      key: 'take',
      width: 110,
      render: (_, row) =>
        row.serialNo ? (
          1
        ) : (
          <InputNumber<number>
            size="small"
            min={0.001}
            max={row.availableQuantity}
            disabled={!selected.includes(row.transactionId)}
            value={quantityOf(row)}
            onChange={(value) => setQuantities((current) => ({ ...current, [row.transactionId]: value }))}
            style={{ width: '100%' }}
          />
        ),
    },
  ];

  return (
    <Modal
      open
      width={1100}
      title={`Chọn chứng từ nhập (F8)${productLabel ? ` - ${productLabel}` : ''}`}
      okText="Chọn"
      cancelText="Hủy"
      onOk={confirm}
      onCancel={onCancel}
      zIndex={1020}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
        Số lượng theo đơn vị cơ sở ({baseUnitName}). Xếp theo hạn dùng sớm nhất (FEFO). Mỗi chứng từ / lô được
        chọn thành một dòng đơn bán.
      </Typography.Paragraph>
      {query.isError && <Alert type="error" showIcon message="Không tải được chứng từ nhập." />}
      <Table<ReceiptSource>
        size="small"
        rowKey="transactionId"
        loading={query.isLoading}
        dataSource={rows}
        columns={columns}
        pagination={false}
        scroll={{ x: 1000, y: 400 }}
        locale={{ emptyText: 'Không còn chứng từ nhập có tồn tại kho này.' }}
        rowSelection={{
          selectedRowKeys: selected,
          onChange: (keys) => {
            setSelected(keys as number[]);
            setError(null);
          },
        }}
      />
      {needsReason && (
        <Input
          style={{ marginTop: 12 }}
          maxLength={500}
          placeholder="Lý do chọn lô khác lô hết hạn sớm nhất (bắt buộc)"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      )}
      {error && <Alert style={{ marginTop: 12 }} type="error" showIcon message={error} />}
    </Modal>
  );
}
