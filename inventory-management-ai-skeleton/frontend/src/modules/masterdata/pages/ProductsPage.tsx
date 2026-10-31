import {
  CheckCircleOutlined,
  EditOutlined,
  EyeOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
import { formValues } from '../../../shared/forms';
import { productApi } from '../api';
import { ImportModal } from '../components/ImportModal';
import { ProductForm } from '../components/ProductForm';
import {
  NEW_PRODUCT_VALUES,
  PRODUCT_FIELDS,
  productPayload,
  type ProductFormValues,
} from '../components/productFields';
import { PRODUCT_TYPES, toOptions } from '../labels';
import { useCatalog, useCategories, useCompanyId, useWarehouses } from '../lookups';
import type { Product, ProductType } from '../types';

function ProductDetail({ productId, onClose }: { productId: number; onClose(): void }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const units = useCatalog('units');
  const warehouses = useWarehouses();
  const [unitForm] = Form.useForm<{ unitId: number; conversionFactor: number; effectiveFrom: Dayjs }>();
  const query = useQuery({
    queryKey: ['products', 'detail', productId],
    queryFn: () => productApi.get(productId),
  });
  const product = query.data;
  const canManage = can('product:manage');

  const addUnit = async () => {
    const values = await unitForm.validateFields();
    try {
      await productApi.addUnit(productId, {
        unitId: values.unitId,
        conversionFactor: values.conversionFactor,
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
      });
      message.success('Đã thêm đơn vị quy đổi.');
      unitForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ['products'] });
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  return (
    <Drawer open width={720} title={product ? `${product.code} - ${product.name}` : ''} onClose={onClose}>
      {product && (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="Loại hàng">{PRODUCT_TYPES[product.productType]}</Descriptions.Item>
            <Descriptions.Item label="ĐVT cơ sở">{units.nameOf(product.baseUnitId)}</Descriptions.Item>
            <Descriptions.Item label="Quản lý">
              {[
                product.lotTracked && 'Lô',
                product.expiryTracked && 'Hạn dùng',
                product.serialTracked && 'Serial',
              ]
                .filter(Boolean)
                .join(', ') || 'Không'}
            </Descriptions.Item>
          </Descriptions>
          <div>
            <Typography.Title level={5}>Đơn vị giao dịch và hệ số quy đổi</Typography.Title>
            <Typography.Paragraph type="secondary">
              1 đơn vị giao dịch = hệ số × {units.nameOf(product.baseUnitId)}. Hệ số đã tạo không sửa; đổi hệ
              số thì thêm dòng mới có ngày hiệu lực khác (BR-MD-21).
            </Typography.Paragraph>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={product.units}
              columns={[
                { title: 'Đơn vị', dataIndex: 'unitId', render: (id: number) => units.nameOf(id) },
                { title: 'Hệ số', dataIndex: 'conversionFactor' },
                {
                  title: 'Hiệu lực từ',
                  dataIndex: 'effectiveFrom',
                  render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
                },
                {
                  title: 'Trạng thái',
                  dataIndex: 'status',
                  render: (v: RecordStatus) => <StatusTag status={v} />,
                },
              ]}
            />
            {canManage && (
              <Form form={unitForm} layout="inline" style={{ marginTop: 12, rowGap: 8 }}>
                <Form.Item name="unitId" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
                  <Select
                    placeholder="Đơn vị"
                    style={{ width: 140 }}
                    options={units.options.filter((option) => option.value !== product.baseUnitId)}
                  />
                </Form.Item>
                <Form.Item name="conversionFactor" rules={[{ required: true, message: 'Nhập hệ số' }]}>
                  <InputNumber placeholder="Hệ số" min={0.000001} style={{ width: 120 }} />
                </Form.Item>
                <Form.Item name="effectiveFrom" rules={[{ required: true, message: 'Chọn ngày' }]}>
                  <DatePicker placeholder="Hiệu lực từ" format="DD/MM/YYYY" />
                </Form.Item>
                <Button onClick={addUnit}>Thêm</Button>
              </Form>
            )}
          </div>
          <div>
            <Typography.Title level={5}>Định mức tồn theo kho</Typography.Title>
            <Table
              size="small"
              rowKey="warehouseId"
              pagination={false}
              dataSource={product.stockLevels}
              locale={{ emptyText: 'Chưa đặt định mức' }}
              columns={[
                { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.nameOf(id) },
                { title: 'Tối thiểu', dataIndex: 'minQuantity' },
                { title: 'Tối đa', dataIndex: 'maxQuantity' },
              ]}
            />
          </div>
        </Space>
      )}
    </Drawer>
  );
}

/** Hàng hóa (BR-MD-01..09, BR-MD-12), đơn vị quy đổi, nhập Excel (BR-UX-05). */
export function ProductsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const categories = useCategories();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [categoryId, setCategoryId] = useState<number>();
  const [productType, setProductType] = useState<ProductType>();
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const [viewing, setViewing] = useState<number | null>(null);
  const [importing, setImporting] = useState(false);
  const { tableProps } = useServerTable('products', productApi.list, {
    companyId,
    keyword,
    status,
    categoryId,
    productType,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['products'] });
  const statusChange = useStatusChange(
    productApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('product:manage');

  const save = async (values: ProductFormValues, key: string) => {
    const payload = productPayload(values);
    if (editing === 'new') {
      await productApi.create({ ...payload, companyId }, key);
      message.success('Đã thêm hàng hóa.');
    } else if (editing) {
      // Mã cũ MISA không còn nhập trên màn hình; gửi lại giá trị đang lưu để không bị xóa khi sửa.
      await productApi.update(editing.id, {
        ...payload,
        legacyCode: editing.legacyCode,
        version: editing.version,
      });
      message.success('Đã lưu hàng hóa.');
    }
    await refresh();
  };

  const openEdit = async (product: Product) => {
    try {
      setEditing(await productApi.get(product.id));
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  const columns: TableColumnsType<Product> = [
    { title: 'Mã hàng', dataIndex: 'code', sorter: true, width: 150 },
    { title: 'Tên hàng', dataIndex: 'name', sorter: true },
    { title: 'Nhóm', dataIndex: 'categoryId', width: 180, render: (id: number) => categories.nameOf(id) },
    {
      title: 'Loại',
      dataIndex: 'productType',
      width: 170,
      render: (type: ProductType) => PRODUCT_TYPES[type],
    },
    { title: 'ĐVT', dataIndex: 'baseUnitId', width: 90, render: (id: number) => units.nameOf(id) },
    {
      title: 'Quản lý',
      width: 150,
      render: (_, product) => (
        <Space size={2} wrap>
          {product.lotTracked && <Tag>Lô</Tag>}
          {product.expiryTracked && <Tag>Hạn</Tag>}
          {product.serialTracked && <Tag>Serial</Tag>}
        </Space>
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
      width: 220,
      render: (_, product) => (
        <Space>
          <Button size="small" onClick={() => setViewing(product.id)} icon={<EyeOutlined />}>
            Chi tiết
          </Button>
          {canManage && (
            <>
              <Button size="small" onClick={() => openEdit(product)} icon={<EditOutlined />}>
                Sửa
              </Button>
              <Button
                size="small"
                danger={product.status === 'ACTIVE'}
                onClick={() => statusChange.open(product)}
                icon={product.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              >
                {product.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
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
        Hàng hóa
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên, model"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <>
            <Select
              placeholder="Nhóm hàng"
              allowClear
              value={categoryId}
              onChange={setCategoryId}
              options={categories.options}
              style={{ width: 220 }}
            />
            <Select
              placeholder="Loại hàng"
              allowClear
              value={productType}
              onChange={setProductType}
              options={toOptions(PRODUCT_TYPES)}
              style={{ width: 200 }}
            />
            {canManage && (
              <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>
                Nhập Excel
              </Button>
            )}
          </>
        }
        createLabel="Thêm hàng hóa"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<Product> {...tableProps} columns={columns} scroll={{ x: 1300 }} />

      <FormModal<ProductFormValues>
        open={editing !== null}
        width={860}
        title={editing === 'new' ? 'Thêm hàng hóa' : 'Sửa hàng hóa'}
        initialValues={
          editing && editing !== 'new'
            ? {
                ...(formValues(editing) as ProductFormValues),
                registrationValidTo: editing.registrationValidTo
                  ? dayjs(editing.registrationValidTo)
                  : undefined,
              }
            : NEW_PRODUCT_VALUES
        }
        fieldNames={PRODUCT_FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(modalForm) => <ProductForm form={modalForm} editing={editing === 'new' ? null : editing} />}
      </FormModal>
      {viewing !== null && <ProductDetail productId={viewing} onClose={() => setViewing(null)} />}
      <ImportModal
        open={importing}
        title="Nhập hàng hóa từ Excel"
        onDownloadTemplate={productApi.downloadTemplate}
        onImport={(file, key) => productApi.import(companyId, file, key)}
        onDone={refresh}
        onClose={() => setImporting(false)}
      />
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
