import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { formValues } from '../../../shared/forms';
import { useMyBranches } from '../../system/lookups';
import { warehouseApi } from '../api';
import { LOCATION_ZONES, VIRTUAL_KINDS, toOptions } from '../labels';
import { useCompanyId } from '../lookups';
import type {
  LocationZone,
  VirtualWarehouseKind,
  Warehouse,
  WarehouseLocation,
  WarehouseType,
} from '../types';
import { BRANCH_VISIBLE, whenBranchVisible } from '../../../shared/branch';

const WAREHOUSE_FIELDS = [
  'branchId',
  'code',
  'name',
  'warehouseType',
  'virtualKind',
  'address',
  'storageCondition',
];
const LOCATION_FIELDS = ['code', 'name', 'zoneType', 'storageCondition'];
const CODE_RULES = [
  { required: true, message: 'Nhập mã' },
  { pattern: /^[A-Z0-9_.-]{1,30}$/, message: 'Mã gồm 1-30 ký tự A-Z, 0-9, _ . -' },
];

function LocationsDrawer({ warehouse, onClose }: { warehouse: Warehouse; onClose(): void }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<WarehouseLocation | 'new' | null>(null);
  const query = useQuery({
    queryKey: ['warehouse-locations', warehouse.id],
    queryFn: () => warehouseApi.locations(warehouse.id),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['warehouse-locations', warehouse.id] });
  const statusChange = useStatusChange(warehouseApi.changeLocationStatus, (item) => item.code, refresh);
  const canManage = can('warehouse:manage');

  const save = async (values: Partial<WarehouseLocation>, key: string) => {
    if (editing === 'new') {
      await warehouseApi.addLocation(warehouse.id, values, key);
      message.success('Đã thêm vị trí.');
    } else if (editing) {
      await warehouseApi.updateLocation(editing.id, { ...values, version: editing.version });
      message.success('Đã lưu vị trí.');
    }
    await refresh();
  };

  return (
    <Drawer open width={760} title={`Vị trí kho ${warehouse.code} - ${warehouse.name}`} onClose={onClose}>
      {canManage && (
        <Button type="primary" style={{ marginBottom: 12 }} onClick={() => setEditing('new')}>
          Thêm vị trí
        </Button>
      )}
      <Table<WarehouseLocation>
        rowKey="id"
        size="small"
        pagination={false}
        loading={query.isFetching}
        dataSource={query.data ?? []}
        columns={[
          { title: 'Mã', dataIndex: 'code', width: 120 },
          {
            title: 'Tên vị trí',
            dataIndex: 'name',
            render: (name: string, location) => (
              <Space>
                {name}
                {location.defaultLocation && <Tag color="blue">Mặc định</Tag>}
              </Space>
            ),
          },
          {
            title: 'Khu',
            dataIndex: 'zoneType',
            width: 130,
            render: (zone: LocationZone) => LOCATION_ZONES[zone],
          },
          {
            title: 'Trạng thái',
            dataIndex: 'status',
            width: 140,
            render: (v: RecordStatus) => <StatusTag status={v} />,
          },
          {
            title: 'Thao tác',
            width: 160,
            render: (_, location) =>
              canManage && (
                <Space>
                  <Button size="small" onClick={() => setEditing(location)} icon={<EditOutlined />}>
                    Sửa
                  </Button>
                  {!location.defaultLocation && (
                    <Button
                      size="small"
                      onClick={() => statusChange.open(location)}
                      icon={location.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
                    >
                      {location.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
                    </Button>
                  )}
                </Space>
              ),
          },
        ]}
      />
      <FormModal<Partial<WarehouseLocation>>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm vị trí' : 'Sửa vị trí'}
        initialValues={editing && editing !== 'new' ? formValues(editing) : { zoneType: 'NORMAL' }}
        fieldNames={LOCATION_FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item name="code" label="Mã vị trí" rules={editing === 'new' ? CODE_RULES : []}>
          <Input disabled={editing !== 'new'} maxLength={30} />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên vị trí"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="zoneType" label="Loại khu" rules={[{ required: true }]}>
          <Select options={toOptions(LOCATION_ZONES)} />
        </Form.Item>
        <Form.Item name="storageCondition" label="Điều kiện bảo quản">
          <Input maxLength={200} />
        </Form.Item>
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </Drawer>
  );
}

/** Kho, kho ảo và vị trí (BR-MD-40, BR-MD-41). Chỉ thấy kho trong phạm vi dữ liệu. */
export function WarehousesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [warehouseType, setWarehouseType] = useState<WarehouseType>();
  const [editing, setEditing] = useState<Warehouse | 'new' | null>(null);
  const [locationsOf, setLocationsOf] = useState<Warehouse | null>(null);
  const { tableProps } = useServerTable('warehouses', warehouseApi.list, {
    companyId,
    keyword,
    status,
    warehouseType,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['warehouses'] });
  const statusChange = useStatusChange(
    warehouseApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('warehouse:manage');

  const save = async (values: Partial<Warehouse>, key: string) => {
    if (editing === 'new') {
      await warehouseApi.create(values, key);
      message.success('Đã thêm kho (kèm vị trí mặc định).');
    } else if (editing) {
      await warehouseApi.update(editing.id, {
        name: values.name,
        address: values.address,
        storageCondition: values.storageCondition,
        version: editing.version,
      });
      message.success('Đã lưu kho.');
    }
    await refresh();
  };

  const columns: TableColumnsType<Warehouse> = [
    { title: 'Mã kho', dataIndex: 'code', sorter: true, width: 140 },
    { title: 'Tên kho', dataIndex: 'name', sorter: true },
    ...whenBranchVisible({
      title: 'Chi nhánh',
      dataIndex: 'branchId',
      width: 200,
      render: (id: number) => branches.nameOf(id),
    }),
    {
      title: 'Loại',
      width: 200,
      render: (_, warehouse) =>
        warehouse.warehouseType === 'NORMAL' ? (
          'Kho thường'
        ) : (
          <Tag color="purple">{VIRTUAL_KINDS[warehouse.virtualKind as VirtualWarehouseKind]}</Tag>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 240,
      render: (_, warehouse) => (
        <Space>
          <Button size="small" onClick={() => setLocationsOf(warehouse)}>
            Vị trí
          </Button>
          {canManage && (
            <>
              <Button size="small" onClick={() => setEditing(warehouse)} icon={<EditOutlined />}>
                Sửa
              </Button>
              <Button
                size="small"
                danger={warehouse.status === 'ACTIVE'}
                onClick={() => statusChange.open(warehouse)}
                icon={warehouse.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              >
                {warehouse.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Kho
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên kho"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <Select
            placeholder="Loại kho"
            allowClear
            value={warehouseType}
            onChange={setWarehouseType}
            options={[
              { value: 'NORMAL', label: 'Kho thường' },
              { value: 'VIRTUAL', label: 'Kho ảo' },
            ]}
            style={{ width: 160 }}
          />
        }
        createLabel="Thêm kho"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<Warehouse> {...tableProps} columns={columns} scroll={{ x: 1100 }} />
      <FormModal<Partial<Warehouse>>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm kho' : 'Sửa kho'}
        initialValues={
          editing && editing !== 'new'
            ? formValues(editing)
            : { branchId: branches.defaultId, warehouseType: 'NORMAL' }
        }
        fieldNames={WAREHOUSE_FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <>
            <Form.Item
              name="branchId"
              label="Chi nhánh"
              rules={[{ required: true, message: 'Chọn chi nhánh' }]}
              hidden={!BRANCH_VISIBLE}
            >
              <Select options={branches.options} disabled={editing !== 'new'} />
            </Form.Item>
            <Form.Item name="code" label="Mã kho" rules={editing === 'new' ? CODE_RULES : []}>
              <Input disabled={editing !== 'new'} maxLength={30} />
            </Form.Item>
            <Form.Item
              name="name"
              label="Tên kho"
              rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Space wrap>
              <Form.Item name="warehouseType" label="Loại kho" rules={[{ required: true }]}>
                <Select
                  disabled={editing !== 'new'}
                  style={{ width: 160 }}
                  options={[
                    { value: 'NORMAL', label: 'Kho thường' },
                    { value: 'VIRTUAL', label: 'Kho ảo' },
                  ]}
                  onChange={(type: WarehouseType) => {
                    if (type === 'NORMAL') {
                      form.setFieldValue('virtualKind', undefined);
                    }
                  }}
                />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {() =>
                  form.getFieldValue('warehouseType') === 'VIRTUAL' && (
                    <Form.Item
                      name="virtualKind"
                      label="Loại kho ảo"
                      rules={[{ required: true, message: 'Chọn loại' }]}
                    >
                      <Select
                        disabled={editing !== 'new'}
                        style={{ width: 220 }}
                        options={toOptions(VIRTUAL_KINDS)}
                      />
                    </Form.Item>
                  )
                }
              </Form.Item>
            </Space>
            <Form.Item name="address" label="Địa chỉ">
              <Input maxLength={500} />
            </Form.Item>
            <Form.Item name="storageCondition" label="Điều kiện bảo quản">
              <Input maxLength={200} />
            </Form.Item>
          </>
        )}
      </FormModal>
      {locationsOf && <LocationsDrawer warehouse={locationsOf} onClose={() => setLocationsOf(null)} />}
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
