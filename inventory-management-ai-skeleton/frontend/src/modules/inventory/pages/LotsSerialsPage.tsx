import { useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { stockApi } from '../api';
import { SERIAL_STATUSES, formatDate, formatNumber } from '../labels';
import type { Lot, Serial, SerialStatus } from '../types';

interface BlockTarget {
  kind: 'lots' | 'serials';
  id: number;
  label: string;
  blocked: boolean;
}

/** Hộp thoại khóa / mở khóa lô, serial (BR-INV-23), bắt buộc lý do. */
function BlockModal({ target, onClose }: { target: BlockTarget | null; onClose(): void }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const submit = async (values: { reason: string }, key: string) => {
    if (!target) {
      return;
    }
    await stockApi.block(target.kind, target.id, !target.blocked, values.reason.trim(), key);
    message.success(target.blocked ? 'Đã mở khóa.' : 'Đã khóa.');
    await queryClient.invalidateQueries({
      queryKey: [target.kind === 'lots' ? 'inventory-lots' : 'inventory-serials'],
    });
  };
  return (
    <FormModal<{ reason: string }>
      open={target !== null}
      title={target ? `${target.blocked ? 'Mở khóa' : 'Khóa'} ${target.label}` : ''}
      okText="Xác nhận"
      fieldNames={['reason']}
      onSubmit={submit}
      onClose={onClose}
    >
      <Form.Item
        name="reason"
        label="Lý do"
        rules={[{ required: true, whitespace: true, message: 'Nhập lý do' }]}
      >
        <Input.TextArea rows={3} maxLength={500} />
      </Form.Item>
    </FormModal>
  );
}

function LotsTab({ productId, onBlock }: { productId?: number; onBlock(target: BlockTarget): void }) {
  const can = useCan();
  const companyId = useCompanyId();
  const [keyword, setKeyword] = useState<string>();
  const { tableProps } = useServerTable('inventory-lots', stockApi.lots, { companyId, productId, keyword });
  const columns: TableColumnsType<Lot> = [
    { title: 'Số lô', dataIndex: 'lotNo', sorter: true },
    { title: 'Hàng hóa', render: (_, lot) => `${lot.productCode ?? ''} - ${lot.productName ?? ''}` },
    { title: 'Ngày SX', dataIndex: 'manufactureDate', render: formatDate },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', sorter: true, render: formatDate },
    {
      title: 'Còn (ngày)',
      dataIndex: 'daysToExpiry',
      align: 'right',
      render: (days: number | null) =>
        days === null ? '' : days < 0 ? <Tag color="red">Hết hạn</Tag> : days,
    },
    { title: 'Tồn', dataIndex: 'onHandQuantity', align: 'right', render: (v: number) => formatNumber(v) },
    {
      title: 'Khóa',
      dataIndex: 'blocked',
      render: (blocked: boolean, lot) => (blocked ? <Tag color="red">{lot.blockReason ?? 'Khóa'}</Tag> : ''),
    },
    {
      title: 'Thao tác',
      width: 110,
      render: (_, lot) =>
        can('lot:block') && (
          <Button
            size="small"
            onClick={() =>
              onBlock({ kind: 'lots', id: lot.id, label: `lô ${lot.lotNo}`, blocked: lot.blocked })
            }
          >
            {lot.blocked ? 'Mở khóa' : 'Khóa'}
          </Button>
        ),
    },
  ];
  return (
    <>
      <Input.Search
        allowClear
        placeholder="Tìm số lô"
        style={{ width: 260, marginBottom: 12 }}
        onSearch={(value) => setKeyword(value || undefined)}
      />
      <Table<Lot> {...tableProps} columns={columns} scroll={{ x: 900 }} />
    </>
  );
}

function SerialsTab({ productId, onBlock }: { productId?: number; onBlock(target: BlockTarget): void }) {
  const can = useCan();
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [keyword, setKeyword] = useState<string>();
  const [status, setStatus] = useState<SerialStatus>();
  const { tableProps } = useServerTable('inventory-serials', stockApi.serials, {
    companyId,
    productId,
    keyword,
    status,
  });
  const columns: TableColumnsType<Serial> = [
    { title: 'Serial', dataIndex: 'serialNo', sorter: true },
    { title: 'Hàng hóa', render: (_, serial) => `${serial.productCode ?? ''} - ${serial.productName ?? ''}` },
    { title: 'Tình trạng', dataIndex: 'lifecycleStatus', render: (v: SerialStatus) => SERIAL_STATUSES[v] },
    {
      title: 'Kho hiện tại',
      dataIndex: 'currentWarehouseId',
      render: (id: number | null) => (id ? warehouses.codeOf(id) : ''),
    },
    {
      title: 'Khóa',
      dataIndex: 'blocked',
      render: (blocked: boolean, serial) =>
        blocked ? <Tag color="red">{serial.blockReason ?? 'Khóa'}</Tag> : '',
    },
    {
      title: 'Thao tác',
      width: 110,
      render: (_, serial) =>
        can('lot:block') && (
          <Button
            size="small"
            onClick={() =>
              onBlock({
                kind: 'serials',
                id: serial.id,
                label: `serial ${serial.serialNo}`,
                blocked: serial.blocked,
              })
            }
          >
            {serial.blocked ? 'Mở khóa' : 'Khóa'}
          </Button>
        ),
    },
  ];
  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search
          allowClear
          placeholder="Tìm serial"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value || undefined)}
        />
        <Select
          allowClear
          placeholder="Tình trạng"
          style={{ width: 160 }}
          value={status}
          onChange={setStatus}
          options={(Object.keys(SERIAL_STATUSES) as SerialStatus[]).map((value) => ({
            value,
            label: SERIAL_STATUSES[value],
          }))}
        />
      </Space>
      <Table<Serial> {...tableProps} columns={columns} scroll={{ x: 800 }} />
    </>
  );
}

/** Lô, serial: hạn dùng, tồn theo lô, vòng đời serial, khóa / mở khóa (BR-INV-01, BR-INV-02, BR-INV-23). */
export function LotsSerialsPage() {
  const [productId, setProductId] = useState<number>();
  const [target, setTarget] = useState<BlockTarget | null>(null);
  return (
    <Card>
      <Typography.Title level={4}>Lô, serial</Typography.Title>
      <ProductSelect
        allowClear
        style={{ width: 360, marginBottom: 12 }}
        value={productId}
        onChange={setProductId}
      />
      <Tabs
        items={[
          { key: 'lots', label: 'Lô', children: <LotsTab productId={productId} onBlock={setTarget} /> },
          {
            key: 'serials',
            label: 'Serial',
            children: <SerialsTab productId={productId} onBlock={setTarget} />,
          },
        ]}
      />
      <BlockModal target={target} onClose={() => setTarget(null)} />
    </Card>
  );
}
