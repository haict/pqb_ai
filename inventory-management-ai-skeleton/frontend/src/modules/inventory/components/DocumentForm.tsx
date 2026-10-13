import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, DatePicker, Form, Input, Row, Select, type FormInstance } from 'antd';
import { productApi } from '../../masterdata/api';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { RemoteSelect } from '../../masterdata/components/RemoteSelect';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { policyApi, stockApi } from '../api';
import { CREATABLE_TYPES, DOCUMENT_TYPES } from '../labels';
import type { InventoryDocumentType, Lot, StockIssuePolicy } from '../types';
import type { DocumentFormValues } from './documentFormValues';
import { DecimalInput } from '../../../shared/components/DecimalInput';

const QUALITY_OPTIONS = (Object.keys(QUALITY_STATUSES) as QualityStatus[]).map((value) => ({
  value,
  label: QUALITY_STATUSES[value].label,
}));

/** Loại dòng theo loại chứng từ: nhập (khai báo lô mới, đơn giá) hay xuất (chọn lô có sẵn hoặc để FEFO). */
function isInbound(type: InventoryDocumentType, direction?: string) {
  return type === 'PNK' || (type === 'PDC' && direction === 'IN');
}

function LineFields({
  form,
  name,
  type,
  onRemove,
}: {
  form: FormInstance<DocumentFormValues>;
  name: number;
  type: InventoryDocumentType;
  onRemove(): void;
}) {
  const units = useCatalog('units');
  const productId = Form.useWatch(['lines', name, 'productId'], form);
  const direction = Form.useWatch(['lines', name, 'direction'], form);
  const product = useQuery({
    queryKey: ['products', productId],
    queryFn: () => productApi.get(productId as number),
    enabled: typeof productId === 'number',
  }).data;
  const inbound = isInbound(type, direction);
  const unitOptions = product
    ? [
        product.baseUnitId,
        ...product.units.filter((unit) => unit.status === 'ACTIVE').map((unit) => unit.unitId),
      ]
        .filter((id, index, all) => all.indexOf(id) === index)
        .map((id) => ({ value: id, label: units.nameOf(id) || `#${id}` }))
    : [];

  return (
    <Card
      size="small"
      style={{ marginBottom: 8 }}
      title={`Dòng ${name + 1}`}
      extra={<Button type="text" danger icon={<DeleteOutlined />} onClick={onRemove} />}
    >
      <Row gutter={8}>
        <Col xs={24} md={10}>
          <Form.Item
            name={[name, 'productId']}
            label="Hàng hóa"
            rules={[{ required: true, message: 'Chọn hàng' }]}
          >
            <ProductSelect
              onProduct={(selected) =>
                selected && form.setFieldValue(['lines', name, 'unitId'], selected.baseUnitId)
              }
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name={[name, 'unitId']} label="Đơn vị" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
            <Select options={unitOptions} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item
            name={[name, 'quantity']}
            label="Số lượng"
            rules={[{ required: true, message: 'Nhập số lượng' }]}
          >
            <DecimalInput min={0.001} />
          </Form.Item>
        </Col>
        {type === 'PDC' && (
          <Col xs={12} md={6}>
            <Form.Item name={[name, 'direction']} label="Tăng / giảm" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'IN', label: 'Tăng tồn' },
                  { value: 'OUT', label: 'Giảm tồn' },
                ]}
              />
            </Form.Item>
          </Col>
        )}
        {inbound && (
          <Col xs={12} md={6}>
            <Form.Item
              name={[name, 'unitCost']}
              label="Đơn giá (theo ĐVT)"
              rules={[{ required: true, message: 'Nhập đơn giá' }]}
            >
              <DecimalInput min={0} fractionDigits={4} />
            </Form.Item>
          </Col>
        )}
      </Row>
      {product?.lotTracked && inbound && (
        <Row gutter={8}>
          <Col xs={24} md={8}>
            <Form.Item
              name={[name, 'lotNo']}
              label="Số lô"
              rules={[{ required: true, message: 'Nhập số lô' }]}
            >
              <Input maxLength={50} />
            </Form.Item>
          </Col>
          <Col xs={12} md={8}>
            <Form.Item name={[name, 'manufactureDate']} label="Ngày sản xuất">
              <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={8}>
            <Form.Item
              name={[name, 'expiryDate']}
              label="Hạn dùng"
              rules={[{ required: product.expiryTracked, message: 'Nhập hạn dùng' }]}
            >
              <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
      )}
      {product?.lotTracked && !inbound && (
        <Row gutter={8}>
          <Col xs={24} md={10}>
            <Form.Item
              name={[name, 'lotId']}
              label="Lô"
              extra="Để trống: hệ thống chọn lô hết hạn sớm nhất (FEFO)."
            >
              <RemoteSelect<Lot>
                allowClear
                queryKey={`lots-${product.id}`}
                fetch={(keyword) =>
                  stockApi.lots({
                    page: 0,
                    size: 30,
                    companyId: product.companyId,
                    productId: product.id,
                    keyword,
                  })
                }
                toOption={(lot) => ({
                  value: lot.id,
                  label: `${lot.lotNo}${lot.expiryDate ? ` - HD ${lot.expiryDate}` : ''}`,
                })}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={14}>
            <Form.Item name={[name, 'lotOverrideReason']} label="Lý do chọn lô khác FEFO">
              <Input maxLength={500} />
            </Form.Item>
          </Col>
        </Row>
      )}
      {product?.serialTracked && (
        <Form.Item
          name={[name, 'serialNumbers']}
          label="Serial (mỗi serial một đơn vị cơ sở)"
          rules={[{ required: true, message: 'Nhập serial' }]}
        >
          <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
        </Form.Item>
      )}
      {(type === 'PCL' || type === 'PCK' || (type === 'PDC' && direction)) && (
        <Row gutter={8}>
          <Col xs={12} md={8}>
            <Form.Item
              name={[name, 'qualityStatus']}
              label={type === 'PCL' ? 'Trạng thái hiện tại' : 'Trạng thái chất lượng'}
              rules={[{ required: type === 'PCL', message: 'Chọn trạng thái' }]}
            >
              <Select allowClear options={QUALITY_OPTIONS} />
            </Form.Item>
          </Col>
          {type === 'PCL' && (
            <Col xs={12} md={8}>
              <Form.Item
                name={[name, 'targetQualityStatus']}
                label="Trạng thái mới"
                rules={[{ required: true, message: 'Chọn trạng thái mới' }]}
              >
                <Select options={QUALITY_OPTIONS} />
              </Form.Item>
            </Col>
          )}
        </Row>
      )}
    </Card>
  );
}

/** Nội dung form lập / sửa chứng từ kho; trường hiển thị theo loại chứng từ và cấu hình lô / hạn / serial. */
export function DocumentFormFields({
  form,
  editing,
}: {
  form: FormInstance<DocumentFormValues>;
  editing: boolean;
}) {
  const warehouses = useWarehouses();
  const companyId = useCompanyId();
  const type = (Form.useWatch('documentType', form) ?? 'PNK') as InventoryDocumentType;
  const subType = Form.useWatch('subType', form) as string | undefined;
  return (
    <>
      <Row gutter={8}>
        <Col xs={24} md={8}>
          <Form.Item name="documentType" label="Loại chứng từ" rules={[{ required: true }]}>
            <Select
              disabled={editing}
              options={CREATABLE_TYPES.map((value) => ({ value, label: DOCUMENT_TYPES[value] }))}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={8}>
          <Form.Item name="documentDate" label="Ngày chứng từ" rules={[{ required: true }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={8}>
          <Form.Item name="postingDate" label="Ngày hạch toán" extra="Để trống: bằng ngày chứng từ.">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item
            name="warehouseId"
            label={type === 'PCK' ? 'Kho xuất' : 'Kho'}
            rules={[{ required: true, message: 'Chọn kho' }]}
          >
            <Select showSearch optionFilterProp="label" options={warehouses.options} />
          </Form.Item>
        </Col>
        {type === 'PCK' && (
          <Col xs={24} md={8}>
            <Form.Item
              name="destinationWarehouseId"
              label="Kho nhận"
              rules={[{ required: true, message: 'Chọn kho nhận' }]}
              extra="Khác chi nhánh: hàng qua kho đang vận chuyển, kho nhận xác nhận."
            >
              <Select showSearch optionFilterProp="label" options={warehouses.options} />
            </Form.Item>
          </Col>
        )}
        {type === 'PNK' && (
          <Col xs={24} md={8}>
            <Form.Item name="subType" label="Nghiệp vụ nhập">
              <Select
                allowClear
                placeholder="Nhập kho"
                options={[{ value: 'RECALL_IN', label: 'Nhập hàng thu hồi từ khách' }]}
              />
            </Form.Item>
          </Col>
        )}
        {type === 'PNK' && subType === 'RECALL_IN' && (
          <Col xs={24} md={8}>
            <Form.Item
              name="partnerId"
              label="Khách trả hàng"
              rules={[{ required: true, message: 'Chọn khách' }]}
            >
              <PartnerSelect role="CUSTOMER" />
            </Form.Item>
          </Col>
        )}
        <Col xs={24} md={type === 'PCK' ? 24 : 16}>
          <Form.Item
            name="reasonText"
            label="Lý do"
            rules={[
              {
                required: type === 'PDC' || type === 'PXH' || type === 'PCL',
                whitespace: true,
                message: 'Nhập lý do',
              },
            ]}
          >
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        {(type === 'PXK' || type === 'PCK') && (
          <Col xs={24} md={8}>
            <Form.Item name="issuePolicyId" label="Chính sách xuất vượt tồn (nếu có)">
              <RemoteSelect<StockIssuePolicy>
                allowClear
                queryKey="stock-issue-policies-approved"
                fetch={(keyword) =>
                  policyApi.list({ page: 0, size: 30, companyId, status: 'APPROVED', keyword })
                }
                toOption={(policy) => ({
                  value: policy.id,
                  label: `${policy.documentNo} - ${policy.reason}`,
                })}
              />
            </Form.Item>
          </Col>
        )}
        <Col xs={24}>
          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <LineFields
                key={field.key}
                form={form}
                name={field.name}
                type={type}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              Thêm dòng hàng
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}
