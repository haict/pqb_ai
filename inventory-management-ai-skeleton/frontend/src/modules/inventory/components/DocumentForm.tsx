import { DeleteOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Tooltip,
  type FormInstance,
} from 'antd';
import { productApi } from '../../masterdata/api';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { RemoteSelect } from '../../masterdata/components/RemoteSelect';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import { useCatalog, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { policyApi, stockApi } from '../api';
import { CREATABLE_TYPES, DOCUMENT_TYPES, OPENING_SUB_TYPE, RECEIPT_SUB_TYPES } from '../labels';
import type { InventoryDocumentType, Lot, StockIssuePolicy } from '../types';
import type { DocumentFormValues } from './documentFormValues';
import { DecimalInput } from '../../../shared/components/DecimalInput';
import { LineGrid, LineGridRow, NotApplicable } from '../../../shared/components/LineGrid';
import { lineCellStyle, type LineGridColumn } from '../../../shared/components/lineGridStyles';
import { DATE_INPUT_FORMAT } from '../../../shared/dateInput';

const QUALITY_OPTIONS = (Object.keys(QUALITY_STATUSES) as QualityStatus[]).map((value) => ({
  value,
  label: QUALITY_STATUSES[value].label,
}));

/** Loại dòng theo loại chứng từ: nhập (khai báo lô mới, đơn giá) hay xuất (chọn lô có sẵn hoặc để FEFO). */
function isInbound(type: InventoryDocumentType, direction?: string) {
  return type === 'PNK' || (type === 'PDC' && direction === 'IN');
}

/** Cột bảng dòng hàng theo loại chứng từ; ô không áp dụng cho dòng (hàng không theo lô, dòng giảm tồn) hiện dấu –. */
interface LineLayout {
  columns: LineGridColumn[];
  minWidth: number;
  canReceive: boolean;
  canIssue: boolean;
  showQuality: boolean;
}

function lineLayout(type: InventoryDocumentType, openingStock: boolean): LineLayout {
  const canReceive = type === 'PNK' || type === 'PDC';
  const canIssue = type !== 'PNK';
  const showQuality = type === 'PCL' || type === 'PCK' || type === 'PDC' || openingStock;
  const columns: LineGridColumn[] = [
    { title: 'Hàng hóa', width: '260px' },
    { title: 'Đơn vị', width: '100px' },
    { title: 'Số lượng', width: '100px' },
    ...(type === 'PDC' ? [{ title: 'Tăng / giảm', width: '110px' }] : []),
    ...(canReceive
      ? [
          { title: 'Đơn giá (theo ĐVT)', width: '130px' },
          { title: 'Số lô', width: '120px' },
          { title: 'Ngày sản xuất', width: '125px' },
          { title: 'Hạn dùng', width: '125px' },
          {
            title: 'Máy cũ',
            width: '80px',
            tooltip:
              'Chỉ hàng thuộc nhóm máy; để trống là máy mới. Tồn máy mới / cũ tách theo serial hoặc lô.',
          },
        ]
      : []),
    ...(canIssue
      ? [
          {
            title: 'Lô xuất',
            width: '180px',
            tooltip: 'Để trống: hệ thống chọn lô hết hạn sớm nhất (FEFO).',
          },
          { title: 'Lý do chọn lô khác FEFO', width: '180px' },
        ]
      : []),
    { title: 'Serial', width: '180px', tooltip: 'Gõ serial rồi Enter; mỗi serial một đơn vị cơ sở' },
    ...(showQuality
      ? [
          {
            title: type === 'PCL' ? 'Trạng thái hiện tại' : 'Trạng thái CL',
            width: '140px',
            tooltip: openingStock
              ? 'Để trống: theo cấu hình hàng. Hàng không xác định được lô: nhập lô tạm, chọn Cách ly (D-13).'
              : undefined,
          },
        ]
      : []),
    ...(type === 'PCL' ? [{ title: 'Trạng thái mới', width: '140px' }] : []),
    { title: 'Ghi chú', width: 'minmax(140px, 1fr)' },
  ];
  // Cột "minmax(...)" tính theo bề rộng tối thiểu 140px; cộng khoảng cách cột và nút xóa dòng
  const minWidth = columns.reduce((sum, column) => sum + (parseInt(column.width, 10) || 140) + 4, 40);
  return { columns, minWidth, canReceive, canIssue, showQuality };
}

function LineFields({
  form,
  name,
  type,
  layout,
  machineCategories,
  removable,
  onRemove,
}: {
  form: FormInstance<DocumentFormValues>;
  name: number;
  type: InventoryDocumentType;
  layout: LineLayout;
  machineCategories: Set<number>;
  removable: boolean;
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
  const receiveLot = product?.lotTracked && inbound;
  const machine = product !== undefined && machineCategories.has(product.categoryId);
  const issueLot = product?.lotTracked && !inbound && (type !== 'PDC' || direction === 'OUT');

  return (
    <LineGridRow columns={layout.columns}>
      <Form.Item
        name={[name, 'productId']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Chọn hàng' }]}
      >
        <ProductSelect
          onProduct={(selected) => {
            if (selected) {
              form.setFieldValue(['lines', name, 'unitId'], selected.baseUnitId);
            }
            // Hàng nhóm máy mặc định máy mới (BR-INV-43); hàng khác không gửi tình trạng
            form.setFieldValue(
              ['lines', name, 'itemCondition'],
              selected && machineCategories.has(selected.categoryId) ? 'NEW' : undefined,
            );
          }}
        />
      </Form.Item>
      <Form.Item
        name={[name, 'unitId']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Chọn ĐVT' }]}
      >
        <Select options={unitOptions} />
      </Form.Item>
      <Form.Item
        name={[name, 'quantity']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Nhập số lượng' }]}
      >
        <DecimalInput min={0.001} />
      </Form.Item>
      {type === 'PDC' && (
        <Form.Item
          name={[name, 'direction']}
          style={lineCellStyle}
          rules={[{ required: true, message: 'Chọn tăng / giảm' }]}
        >
          <Select
            options={[
              { value: 'IN', label: 'Tăng tồn' },
              { value: 'OUT', label: 'Giảm tồn' },
            ]}
          />
        </Form.Item>
      )}
      {layout.canReceive &&
        (inbound ? (
          <Form.Item
            name={[name, 'unitCost']}
            style={lineCellStyle}
            rules={[{ required: true, message: 'Nhập đơn giá' }]}
          >
            <DecimalInput min={0} fractionDigits={4} />
          </Form.Item>
        ) : (
          <NotApplicable />
        ))}
      {layout.canReceive &&
        (receiveLot ? (
          <>
            <Form.Item
              name={[name, 'lotNo']}
              style={lineCellStyle}
              rules={[{ required: true, message: 'Nhập số lô' }]}
            >
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name={[name, 'manufactureDate']} style={lineCellStyle}>
              <DatePicker format={DATE_INPUT_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name={[name, 'expiryDate']}
              style={lineCellStyle}
              rules={[{ required: product.expiryTracked, message: 'Nhập hạn dùng' }]}
            >
              <DatePicker format={DATE_INPUT_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
          </>
        ) : (
          <>
            <NotApplicable />
            <NotApplicable />
            <NotApplicable />
          </>
        ))}
      {layout.canReceive &&
        (inbound && machine ? (
          <Form.Item
            name={[name, 'itemCondition']}
            style={{ ...lineCellStyle, textAlign: 'center' }}
            valuePropName="checked"
            getValueProps={(value?: string) => ({ checked: value === 'USED' })}
            normalize={(checked: boolean) => (checked ? 'USED' : 'NEW')}
          >
            <Checkbox aria-label="Máy cũ" />
          </Form.Item>
        ) : (
          <NotApplicable />
        ))}
      {layout.canIssue &&
        (issueLot ? (
          <>
            <Form.Item name={[name, 'lotId']} style={lineCellStyle}>
              <RemoteSelect<Lot>
                allowClear
                placeholder="FEFO"
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
            <Form.Item name={[name, 'lotOverrideReason']} style={lineCellStyle}>
              <Input maxLength={500} />
            </Form.Item>
          </>
        ) : (
          <>
            <NotApplicable />
            <NotApplicable />
          </>
        ))}
      {product?.serialTracked ? (
        <Form.Item
          name={[name, 'serialNumbers']}
          style={lineCellStyle}
          rules={[{ required: true, message: 'Nhập serial' }]}
        >
          <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
        </Form.Item>
      ) : (
        <NotApplicable />
      )}
      {layout.showQuality &&
        (type !== 'PDC' || direction ? (
          <Form.Item
            name={[name, 'qualityStatus']}
            style={lineCellStyle}
            rules={[{ required: type === 'PCL', message: 'Chọn trạng thái' }]}
          >
            <Select allowClear options={QUALITY_OPTIONS} />
          </Form.Item>
        ) : (
          <NotApplicable />
        ))}
      {type === 'PCL' && (
        <Form.Item
          name={[name, 'targetQualityStatus']}
          style={lineCellStyle}
          rules={[{ required: true, message: 'Chọn trạng thái mới' }]}
        >
          <Select options={QUALITY_OPTIONS} />
        </Form.Item>
      )}
      <Form.Item name={[name, 'note']} style={lineCellStyle}>
        <Input maxLength={500} />
      </Form.Item>
      <Tooltip title="Xóa dòng">
        <Button icon={<DeleteOutlined />} aria-label="Xóa dòng" disabled={!removable} onClick={onRemove} />
      </Tooltip>
    </LineGridRow>
  );
}

/**
 * Nội dung form lập / sửa chứng từ kho; trường hiển thị theo loại chứng từ và cấu hình lô / hạn / serial.
 * {@code opening}: form của màn hình tồn kho đầu kỳ, cố định phiếu nhập loại tồn đầu kỳ.
 */
export function DocumentFormFields({
  form,
  editing,
  opening = false,
}: {
  form: FormInstance<DocumentFormValues>;
  editing: boolean;
  opening?: boolean;
}) {
  const warehouses = useWarehouses();
  const companyId = useCompanyId();
  const type = (Form.useWatch('documentType', form) ?? 'PNK') as InventoryDocumentType;
  const subType = Form.useWatch('subType', form) as string | undefined;
  const layout = lineLayout(type, type === 'PNK' && subType === OPENING_SUB_TYPE);
  const machineCategories = useQuery({
    queryKey: ['machine-categories', companyId],
    queryFn: () => stockApi.machineCategories(companyId),
    staleTime: 5 * 60 * 1000,
  }).data;
  return (
    <>
      <Row gutter={8}>
        <Col xs={24} md={8}>
          <Form.Item name="documentType" label="Loại chứng từ" rules={[{ required: true }]}>
            <Select
              disabled={editing || opening}
              options={CREATABLE_TYPES.map((value) => ({ value, label: DOCUMENT_TYPES[value] }))}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={8}>
          <Form.Item name="documentDate" label="Ngày chứng từ" rules={[{ required: true }]}>
            <DatePicker format={DATE_INPUT_FORMAT} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={8}>
          <Form.Item name="postingDate" label="Ngày hạch toán" extra="Để trống: bằng ngày chứng từ.">
            <DatePicker format={DATE_INPUT_FORMAT} style={{ width: '100%' }} />
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
                disabled={opening}
                placeholder="Nhập kho"
                options={Object.entries(RECEIPT_SUB_TYPES).map(([value, label]) => ({ value, label }))}
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
          <LineGrid columns={layout.columns} minWidth={layout.minWidth} onAdd={() => add({ unitCost: 0 })}>
            {fields.map((field) => (
              <LineFields
                key={field.key}
                form={form}
                name={field.name}
                type={type}
                layout={layout}
                machineCategories={new Set(machineCategories ?? [])}
                removable={fields.length > 1}
                onRemove={() => remove(field.name)}
              />
            ))}
          </LineGrid>
        )}
      </Form.List>
    </>
  );
}
