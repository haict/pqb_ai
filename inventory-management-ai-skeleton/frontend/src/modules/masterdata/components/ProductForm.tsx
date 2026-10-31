import {
  Alert,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Tabs,
  type FormInstance,
} from 'antd';
import { PRODUCT_TYPES, toOptions } from '../labels';
import { useCatalog, useCategories, useCountries, useTaxRates } from '../lookups';
import type { Product, ProductType } from '../types';
import { trackingDefaults, type ProductFormValues } from './productFields';

export function ProductForm({
  form,
  editing,
}: {
  form: FormInstance<ProductFormValues>;
  editing: Product | null;
}) {
  const units = useCatalog('units');
  const brands = useCatalog('brands');
  const manufacturers = useCatalog('manufacturers');
  const categories = useCategories();
  const taxRates = useTaxRates();
  const countries = useCountries();
  const locked = editing?.structureLocked ?? false;

  return (
    <Tabs
      items={[
        {
          key: 'general',
          label: 'Thông tin chung',
          forceRender: true,
          children: (
            <>
              <Space wrap>
                <Form.Item
                  name="code"
                  label="Mã hàng"
                  rules={
                    editing
                      ? []
                      : [
                          { required: true, message: 'Nhập mã' },
                          { pattern: /^[A-Za-z0-9_.-]{1,50}$/, message: 'Mã gồm 1-50 ký tự chữ, số, _ . -' },
                        ]
                  }
                >
                  <Input disabled={editing !== null} style={{ width: 200 }} />
                </Form.Item>
              </Space>
              <Form.Item
                name="name"
                label="Tên hàng"
                rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
              >
                <Input maxLength={255} />
              </Form.Item>
              <Form.Item name="tradeName" label="Tên thương mại">
                <Input maxLength={255} />
              </Form.Item>
              <Space wrap>
                <Form.Item
                  name="categoryId"
                  label="Nhóm hàng"
                  rules={[{ required: true, message: 'Chọn nhóm' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={categories.options}
                    style={{ width: 260 }}
                  />
                </Form.Item>
                <Form.Item
                  name="productType"
                  label="Loại hàng"
                  rules={[{ required: true, message: 'Chọn loại' }]}
                >
                  <Select
                    options={toOptions(PRODUCT_TYPES)}
                    disabled={locked}
                    style={{ width: 220 }}
                    onChange={(type: ProductType) => {
                      if (!editing) {
                        form.setFieldsValue(trackingDefaults(type));
                      }
                    }}
                  />
                </Form.Item>
                <Form.Item
                  name="baseUnitId"
                  label="ĐVT cơ sở"
                  rules={[{ required: true, message: 'Chọn ĐVT' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={units.options}
                    disabled={locked}
                    style={{ width: 180 }}
                  />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="brandId" label="Nhãn hiệu">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={brands.options}
                    style={{ width: 200 }}
                  />
                </Form.Item>
                <Form.Item name="manufacturerId" label="Nhà sản xuất">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={manufacturers.options}
                    style={{ width: 220 }}
                  />
                </Form.Item>
                <Form.Item name="originCountryCode" label="Xuất xứ">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={countries.options}
                    style={{ width: 180 }}
                  />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="model" label="Model">
                  <Input maxLength={100} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item name="defaultTaxRateId" label="Thuế suất mặc định">
                  <Select allowClear options={taxRates.options} style={{ width: 220 }} />
                </Form.Item>
              </Space>
              <Form.Item name="specification" label="Quy cách">
                <Input maxLength={500} />
              </Form.Item>
              <Form.Item name="description" label="Mô tả kỹ thuật">
                <Input.TextArea rows={2} maxLength={2000} />
              </Form.Item>
            </>
          ),
        },
        {
          key: 'tracking',
          label: 'Quản lý & pháp lý',
          forceRender: true,
          children: (
            <>
              {locked && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Hàng đã phát sinh giao dịch kho: không đổi loại hàng, ĐVT cơ sở, quản lý lô/hạn dùng/serial (BR-MD-04)."
                />
              )}
              <Space wrap>
                <Form.Item name="lotTracked" valuePropName="checked">
                  <Checkbox disabled={locked}>Quản lý theo lô</Checkbox>
                </Form.Item>
                <Form.Item name="expiryTracked" valuePropName="checked">
                  <Checkbox disabled={locked}>Quản lý hạn dùng</Checkbox>
                </Form.Item>
                <Form.Item name="serialTracked" valuePropName="checked">
                  <Checkbox disabled={locked}>Quản lý serial</Checkbox>
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="medicalDeviceClass" label="Phân loại TTBYT">
                  <Select
                    allowClear
                    options={['A', 'B', 'C', 'D'].map((v) => ({ value: v, label: v }))}
                    style={{ width: 120 }}
                  />
                </Form.Item>
                <Form.Item name="registrationNo" label="Số lưu hành / GPNK">
                  <Input maxLength={100} style={{ width: 220 }} />
                </Form.Item>
                <Form.Item name="registrationValidTo" label="Hiệu lực đến">
                  <DatePicker format="DD/MM/YYYY" />
                </Form.Item>
              </Space>
              <Form.Item name="registrationHolder" label="Chủ sở hữu số lưu hành">
                <Input maxLength={255} />
              </Form.Item>
            </>
          ),
        },
        {
          key: 'storage',
          label: 'Bảo quản & bảo hành',
          forceRender: true,
          children: (
            <>
              <Space wrap>
                <Form.Item name="storageMinTemp" label="Nhiệt độ tối thiểu (°C)">
                  <InputNumber min={-100} max={100} />
                </Form.Item>
                <Form.Item name="storageMaxTemp" label="Nhiệt độ tối đa (°C)">
                  <InputNumber min={-100} max={100} />
                </Form.Item>
                <Form.Item name="slowMovingDays" label="Ngưỡng tồn lâu (ngày)">
                  <InputNumber min={1} precision={0} />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="coldChain" valuePropName="checked">
                  <Checkbox>Hàng lạnh</Checkbox>
                </Form.Item>
                <Form.Item name="hazardous" valuePropName="checked">
                  <Checkbox>Hàng nguy hiểm</Checkbox>
                </Form.Item>
                <Form.Item name="requiresInstallation" valuePropName="checked">
                  <Checkbox>Cần lắp đặt</Checkbox>
                </Form.Item>
                <Form.Item name="requiresCalibration" valuePropName="checked">
                  <Checkbox>Cần hiệu chuẩn / kiểm định</Checkbox>
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="warrantyMonths" label="Bảo hành (tháng)">
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item name="maintenanceCycleMonths" label="Chu kỳ bảo trì (tháng)">
                  <InputNumber min={0} precision={0} />
                </Form.Item>
              </Space>
            </>
          ),
        },
      ]}
    />
  );
}
