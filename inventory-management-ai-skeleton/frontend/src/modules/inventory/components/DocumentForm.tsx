import { DeleteOutlined, FileSearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  App,
  Button,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  Row,
  Select,
  Space,
  Tooltip,
  type FormInstance,
} from 'antd';
import { productApi } from '../../masterdata/api';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { RemoteSelect } from '../../masterdata/components/RemoteSelect';
import { QUALITY_STATUSES } from '../../masterdata/labels';
import {
  useCachedSuggestedWarehouse,
  useCatalog,
  useCompanyId,
  useSuggestedWarehouse,
  useWarehouses,
} from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import type { QualityStatus } from '../../masterdata/types';
import { policyApi, stockApi } from '../api';
import { CREATABLE_TYPES, DOCUMENT_TYPES, OPENING_SUB_TYPE, RECEIPT_SUB_TYPES } from '../labels';
import type { InventoryDocumentType, Lot, StockIssuePolicy } from '../types';
import { hasLineWarehouse, type DocumentFormValues } from './documentFormValues';
import { useEffect, useState } from 'react';
import { ReceiptSourcePicker } from '../../sales/components/ReceiptSourcePicker';
import { issueLinesFromReceipts } from './receiptPicks';
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
    ...(hasLineWarehouse(type)
      ? [{ title: 'Kho', width: '170px', tooltip: 'Tự gợi ý theo tên nhóm hàng; sửa được' }]
      : []),
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
            tooltip:
              type === 'PXK'
                ? 'Để trống: hệ thống chọn lô hết hạn sớm nhất (FEFO). F8: chọn theo chứng từ nhập'
                : 'Để trống: hệ thống chọn lô hết hạn sớm nhất (FEFO).',
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
  const warehouses = useWarehouses();
  const productId = Form.useWatch(['lines', name, 'productId'], form);
  const branchId = Form.useWatch('branchId', form);
  const lineWarehouse = hasLineWarehouse(type);
  const suggested = useSuggestedWarehouse(lineWarehouse ? branchId : undefined, productId);
  const cachedSuggestion = useCachedSuggestedWarehouse();
  useEffect(() => {
    // D-77: kho trống thì điền kho gợi ý theo nhóm hàng; không đè kho đã chọn.
    if (suggested && !form.getFieldValue(['lines', name, 'warehouseId'])) {
      form.setFieldValue(['lines', name, 'warehouseId'], suggested);
    }
  }, [form, name, productId, suggested]);
  const warehouseOptions = warehouses.items
    .filter((warehouse) => warehouse.branchId === branchId)
    .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} - ${warehouse.name}` }));
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
  const { message } = App.useApp();
  const [picker, setPicker] = useState<number | null>(null);
  /** F8 trên phiếu xuất kho (D-79): chọn chứng từ nhập còn tồn của hàng tại kho của dòng. */
  const openPicker = () => {
    const warehouseId = form.getFieldValue(['lines', name, 'warehouseId']) as number | undefined;
    if (type !== 'PXK') {
      return;
    }
    if (!product) {
      message.info('Chọn hàng trước khi nhấn F8.');
    } else if (!warehouseId) {
      message.info('Chọn kho của dòng trước khi nhấn F8.');
    } else if (!product.lotTracked && !product.serialTracked) {
      message.info('Hàng không quản lý lô / serial: kho xuất theo FEFO khi ghi sổ.');
    } else {
      setPicker(warehouseId);
    }
  };

  return (
    <LineGridRow
      columns={layout.columns}
      lineNo={name + 1}
      onKeyDown={(event) => {
        if (event.key === 'F8' && type === 'PXK') {
          event.preventDefault();
          openPicker();
        }
      }}
      below={
        picker !== null &&
        product && (
          <ReceiptSourcePicker
            warehouseId={picker}
            productId={product.id}
            productLabel={`${product.code} - ${product.name}`}
            baseUnitName={units.nameOf(product.baseUnitId) || ''}
            targetLabel="phiếu xuất"
            fetchSources={() => stockApi.receiptSources(picker, product.id)}
            onCancel={() => setPicker(null)}
            onPick={(picks, reason) => {
              const lines = [
                ...((form.getFieldValue('lines') as DocumentFormValues['lines'] | undefined) ?? []),
              ];
              const current = lines[name];
              if (current) {
                lines.splice(name, 1, ...issueLinesFromReceipts(current, picks, product.baseUnitId, reason));
                form.setFieldValue('lines', lines);
              }
              setPicker(null);
            }}
          />
        )
      }
    >
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
            // Đổi hàng: kho theo gợi ý của hàng mới (không có gợi ý thì người dùng chọn).
            form.setFieldValue(
              ['lines', name, 'warehouseId'],
              selected && lineWarehouse ? cachedSuggestion(branchId, selected.id) : undefined,
            );
            // Hàng nhóm máy mặc định máy mới (BR-INV-43); hàng khác không gửi tình trạng
            form.setFieldValue(
              ['lines', name, 'itemCondition'],
              selected && machineCategories.has(selected.categoryId) ? 'NEW' : undefined,
            );
          }}
        />
      </Form.Item>
      {lineWarehouse && (
        <Form.Item
          name={[name, 'warehouseId']}
          style={lineCellStyle}
          rules={[{ required: true, message: 'Chọn kho' }]}
        >
          <Select showSearch optionFilterProp="label" options={warehouseOptions} />
        </Form.Item>
      )}
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
            <Space.Compact style={{ ...lineCellStyle, display: 'flex' }}>
              <Form.Item name={[name, 'lotId']} noStyle>
                <RemoteSelect<Lot>
                  allowClear
                  placeholder="FEFO"
                  style={{ flex: 1, minWidth: 0 }}
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
              {type === 'PXK' && (
                <Tooltip title="Chọn chứng từ nhập (F8)">
                  <Button
                    icon={<FileSearchOutlined />}
                    aria-label="Chọn chứng từ nhập"
                    onClick={openPicker}
                  />
                </Tooltip>
              )}
            </Space.Compact>
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
  const branches = useMyBranches();
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
        {hasLineWarehouse(type) ? (
          <Col xs={24} md={8}>
            <Form.Item
              name="branchId"
              label="Chi nhánh"
              rules={[{ required: true, message: 'Chọn chi nhánh' }]}
              extra="Kho chọn theo từng dòng hàng."
            >
              <Select
                disabled={editing}
                options={branches.options}
                onChange={() =>
                  form.setFieldValue(
                    'lines',
                    (form.getFieldValue('lines') as DocumentFormValues['lines'] | undefined)?.map((line) => ({
                      ...line,
                      warehouseId: undefined,
                    })) ?? [],
                  )
                }
              />
            </Form.Item>
          </Col>
        ) : (
          <Col xs={24} md={8}>
            <Form.Item name="warehouseId" label="Kho xuất" rules={[{ required: true, message: 'Chọn kho' }]}>
              <Select showSearch optionFilterProp="label" options={warehouses.options} />
            </Form.Item>
          </Col>
        )}
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
