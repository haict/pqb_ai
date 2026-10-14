import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { priceListApi } from '../api';
import { PartnerSelect, ProductSelect } from '../components/EntitySelects';
import { PRICE_TARGETS, PRICE_TYPES, toOptions } from '../labels';
import { useCatalog, useCompanyId } from '../lookups';
import type { PriceList, PriceListItem, PriceTargetType, PriceType, Product } from '../types';

const FIELDS = [
  'code',
  'name',
  'priceType',
  'targetType',
  'partnerGroupId',
  'partnerId',
  'currencyCode',
  'validFrom',
  'validTo',
];

interface FormValues extends Omit<Partial<PriceList>, 'validFrom' | 'validTo'> {
  validFrom?: Dayjs;
  validTo?: Dayjs;
}

function formatMoney(value: number) {
  return Number(value).toLocaleString('vi-VN');
}

function ItemsDrawer({ priceList, onClose }: { priceList: PriceList; onClose(): void }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const units = useCatalog('units');
  const [form] = Form.useForm<{
    productId: number;
    unitId: number;
    minQuantity?: number;
    unitPrice: number;
  }>();
  const [product, setProduct] = useState<Product>();
  const { tableProps } = useServerTable(
    'price-list-items',
    (params) => priceListApi.items(priceList.id, params),
    { priceListId: priceList.id },
  );
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['price-list-items'] });
  const canManage = can('price:manage');
  const unitOptions = product
    ? units.options.filter(
        (option) =>
          option.value === product.baseUnitId || product.units.some((unit) => unit.unitId === option.value),
      )
    : [];

  const upsert = async () => {
    const values = await form.validateFields();
    try {
      await priceListApi.upsertItem(priceList.id, values);
      message.success('Đã lưu giá.');
      form.resetFields();
      setProduct(undefined);
      await refresh();
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  const remove = async (item: PriceListItem) => {
    try {
      await priceListApi.deleteItem(priceList.id, item.id);
      await refresh();
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  return (
    <Drawer open width={860} title={`Giá trong bảng ${priceList.code} - ${priceList.name}`} onClose={onClose}>
      {canManage && (
        <Form form={form} layout="inline" style={{ marginBottom: 12, rowGap: 8 }}>
          <Form.Item name="productId" rules={[{ required: true, message: 'Chọn hàng' }]}>
            <ProductSelect
              style={{ width: 280 }}
              onProduct={(selected) => {
                setProduct(selected);
                form.setFieldValue('unitId', selected?.baseUnitId);
              }}
            />
          </Form.Item>
          <Form.Item name="unitId" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
            <Select placeholder="ĐVT" style={{ width: 110 }} options={unitOptions} />
          </Form.Item>
          <Form.Item name="minQuantity">
            <InputNumber placeholder="Từ SL" min={0} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="unitPrice" rules={[{ required: true, message: 'Nhập giá' }]}>
            <InputNumber placeholder="Đơn giá" min={0} style={{ width: 150 }} />
          </Form.Item>
          <Button type="primary" onClick={upsert}>
            Lưu giá
          </Button>
        </Form>
      )}
      <Table<PriceListItem>
        {...tableProps}
        size="small"
        columns={[
          { title: 'Mã hàng', dataIndex: 'productCode', width: 140 },
          { title: 'Tên hàng', dataIndex: 'productName' },
          { title: 'ĐVT', dataIndex: 'unitId', width: 90, render: (id: number) => units.nameOf(id) },
          { title: 'Từ số lượng', dataIndex: 'minQuantity', sorter: true, width: 110 },
          {
            title: 'Đơn giá',
            dataIndex: 'unitPrice',
            sorter: true,
            width: 140,
            align: 'right',
            render: formatMoney,
          },
          {
            title: '',
            width: 80,
            render: (_, item) =>
              canManage && (
                <Popconfirm
                  title="Xóa giá này?"
                  okText="Xóa"
                  cancelText="Không"
                  onConfirm={() => remove(item)}
                >
                  <Button size="small" danger>
                    Xóa
                  </Button>
                </Popconfirm>
              ),
          },
        ]}
      />
    </Drawer>
  );
}

/** Bảng giá: giá mua tham khảo, niêm yết, giá sàn, bảng giá bán (BR-MD-42). */
export function PriceListsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const groups = useCatalog('partner-groups');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [priceType, setPriceType] = useState<PriceType>();
  const [editing, setEditing] = useState<PriceList | 'new' | null>(null);
  const [viewing, setViewing] = useState<PriceList | null>(null);
  const { tableProps } = useServerTable('price-lists', priceListApi.list, {
    companyId,
    keyword,
    status,
    priceType,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['price-lists'] });
  const statusChange = useStatusChange(
    priceListApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('price:manage');
  const typeOptions = toOptions(PRICE_TYPES).filter(
    (option) => option.value !== 'PURCHASE_REFERENCE' || can('purchase-price:view'),
  );

  const save = async (values: FormValues, key: string) => {
    const payload = {
      ...values,
      partnerGroupId: values.targetType === 'PARTNER_GROUP' ? values.partnerGroupId : null,
      partnerId: values.targetType === 'PARTNER' ? values.partnerId : null,
      validFrom: values.validFrom?.format('YYYY-MM-DD'),
      validTo: values.validTo ? values.validTo.format('YYYY-MM-DD') : null,
    };
    if (editing === 'new') {
      await priceListApi.create({ ...payload, companyId }, key);
      message.success('Đã thêm bảng giá.');
    } else if (editing) {
      await priceListApi.update(editing.id, { ...payload, version: editing.version });
      message.success('Đã lưu bảng giá.');
    }
    await refresh();
  };

  const columns: TableColumnsType<PriceList> = [
    { title: 'Mã', dataIndex: 'code', sorter: true, width: 140 },
    { title: 'Tên bảng giá', dataIndex: 'name', sorter: true },
    { title: 'Loại', dataIndex: 'priceType', width: 170, render: (type: PriceType) => PRICE_TYPES[type] },
    {
      title: 'Áp dụng',
      dataIndex: 'targetType',
      width: 160,
      render: (target: PriceTargetType) => PRICE_TARGETS[target],
    },
    {
      title: 'Hiệu lực',
      width: 210,
      render: (_, list) =>
        `${dayjs(list.validFrom).format('DD/MM/YYYY')} - ${list.validTo ? dayjs(list.validTo).format('DD/MM/YYYY') : '...'}`,
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
      render: (_, list) => (
        <Space>
          <Button size="small" onClick={() => setViewing(list)}>
            Giá
          </Button>
          {canManage && (
            <>
              <Button size="small" onClick={() => setEditing(list)} icon={<EditOutlined />}>
                Sửa
              </Button>
              <Button
                size="small"
                danger={list.status === 'ACTIVE'}
                onClick={() => statusChange.open(list)}
                icon={list.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              >
                {list.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
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
        Bảng giá
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <Select
            placeholder="Loại bảng giá"
            allowClear
            value={priceType}
            onChange={setPriceType}
            options={typeOptions}
            style={{ width: 200 }}
          />
        }
        createLabel="Thêm bảng giá"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<PriceList> {...tableProps} columns={columns} scroll={{ x: 1200 }} />
      <FormModal<FormValues>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm bảng giá' : 'Sửa bảng giá'}
        initialValues={
          editing && editing !== 'new'
            ? {
                ...editing,
                partnerGroupId: editing.partnerGroupId ?? undefined,
                partnerId: editing.partnerId ?? undefined,
                validFrom: dayjs(editing.validFrom),
                validTo: editing.validTo ? dayjs(editing.validTo) : undefined,
              }
            : { priceType: 'SALES', targetType: 'ALL', currencyCode: 'VND', validFrom: dayjs() }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <>
            <Form.Item
              name="code"
              label="Mã bảng giá"
              rules={
                editing === 'new'
                  ? [
                      { required: true, message: 'Nhập mã' },
                      { pattern: /^[A-Z0-9_.-]{1,30}$/, message: 'Mã gồm 1-30 ký tự A-Z, 0-9, _ . -' },
                    ]
                  : []
              }
            >
              <Input disabled={editing !== 'new'} maxLength={30} />
            </Form.Item>
            <Form.Item
              name="name"
              label="Tên"
              rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Space wrap>
              <Form.Item name="priceType" label="Loại" rules={[{ required: true }]}>
                <Select options={typeOptions} disabled={editing !== 'new'} style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name="targetType" label="Áp dụng cho" rules={[{ required: true }]}>
                <Select options={toOptions(PRICE_TARGETS)} style={{ width: 180 }} />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {() =>
                  form.getFieldValue('targetType') === 'PARTNER_GROUP' ? (
                    <Form.Item
                      name="partnerGroupId"
                      label="Nhóm"
                      rules={[{ required: true, message: 'Chọn nhóm' }]}
                    >
                      <Select options={groups.options} style={{ width: 220 }} />
                    </Form.Item>
                  ) : form.getFieldValue('targetType') === 'PARTNER' ? (
                    <Form.Item
                      name="partnerId"
                      label="Đối tác"
                      rules={[{ required: true, message: 'Chọn đối tác' }]}
                    >
                      <PartnerSelect style={{ width: 280 }} />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
            </Space>
            <Space wrap>
              <Form.Item name="currencyCode" label="Tiền tệ">
                <Input maxLength={3} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item
                name="validFrom"
                label="Hiệu lực từ"
                rules={[{ required: true, message: 'Chọn ngày' }]}
              >
                <DatePicker format="DD/MM/YYYY" />
              </Form.Item>
              <Form.Item name="validTo" label="Đến">
                <DatePicker format="DD/MM/YYYY" />
              </Form.Item>
            </Space>
          </>
        )}
      </FormModal>
      {viewing && <ItemsDrawer priceList={viewing} onClose={() => setViewing(null)} />}
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
