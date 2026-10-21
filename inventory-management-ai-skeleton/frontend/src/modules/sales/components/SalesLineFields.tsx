import { DeleteOutlined, FileSearchOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Tag,
  Tooltip,
  Typography,
  type FormInstance,
} from 'antd';
import { useState } from 'react';
import { formatMoney } from '../../accounting/labels';
import { moneyInputProps } from '../../finance/components/debtInputs';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { useTaxRates } from '../../masterdata/lookups';
import { useProductUnits } from '../../purchasing/components/productUnits';
import { salesPriceApi } from '../api';
import { PRICE_SOURCES } from '../labels';
import { ReceiptSourcePicker } from './ReceiptSourcePicker';
import { linesFromReceipts, type SalesLineForm } from './salesLines';
import { DecimalInput } from '../../../shared/components/DecimalInput';

/**
 * Danh sách dòng đơn bán trong form (tên trường {@code lines}): giá, chiết khấu, thuế, lô / serial chỉ định. F8 trên
 * một dòng (hoặc nút "Chọn CT nhập") mở danh sách chứng từ nhập còn tồn để xuất đích danh (D-63); form cần có
 * {@code branchId}, {@code warehouseId}.
 */
export function SalesLinesEditor<T extends { lines?: SalesLineForm[]; customerId?: number }>({
  form,
  withExpectedDate,
}: {
  form: FormInstance<T>;
  withExpectedDate?: boolean;
}) {
  return (
    <Form.List name="lines">
      {(fields, { add, remove }) => (
        <>
          {fields.map((field) => (
            <SalesLineFields
              key={field.key}
              form={form as unknown as FormInstance<{ lines?: SalesLineForm[]; customerId?: number }>}
              name={field.name}
              withExpectedDate={withExpectedDate}
              onRemove={() => remove(field.name)}
            />
          ))}
          <Button icon={<PlusOutlined />} onClick={() => add({ quantity: 1 })}>
            Thêm dòng hàng
          </Button>
        </>
      )}
    </Form.List>
  );
}

function SalesLineFields({
  form,
  name,
  withExpectedDate,
  onRemove,
}: {
  form: FormInstance<{ lines?: SalesLineForm[]; customerId?: number }>;
  name: number;
  withExpectedDate?: boolean;
  onRemove(): void;
}) {
  const { message } = App.useApp();
  const taxRates = useTaxRates();
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const hint = Form.useWatch(['lines', name, 'priceHint'], form) as string | undefined;
  const receiptNo = Form.useWatch(['lines', name, 'receiptNo'], form) as string | undefined;
  const { options, product, unitName } = useProductUnits(productId);
  const [picker, setPicker] = useState<{ branchId: number; warehouseId: number } | null>(null);

  const clearReceipt = () => {
    form.setFieldValue(['lines', name, 'receiptTransactionId'], undefined);
    form.setFieldValue(['lines', name, 'receiptNo'], undefined);
  };

  /** F8: chọn chứng từ nhập còn tồn của hàng tại kho của đơn (D-63). */
  const openPicker = () => {
    const header = form.getFieldsValue(true) as { branchId?: number; warehouseId?: number };
    if (!productId || !product) {
      message.info('Chọn hàng trước khi nhấn F8.');
    } else if (!header.branchId || !header.warehouseId) {
      message.info('Chọn chi nhánh và kho xuất trước khi nhấn F8.');
    } else if (!product.lotTracked && !product.serialTracked) {
      message.info('Hàng không quản lý lô / serial: kho xuất theo FEFO khi ghi sổ.');
    } else {
      setPicker({ branchId: header.branchId, warehouseId: header.warehouseId });
    }
  };

  /** Tra giá đề xuất khi đổi hàng / đơn vị / số lượng (BR-SAL-02); chỉ điền khi người dùng chưa nhập. */
  const suggest = async (overwrite: boolean) => {
    const line = form.getFieldValue(['lines', name]) as SalesLineForm | undefined;
    if (!line?.productId || !line.unitId) {
      return;
    }
    try {
      const price = await salesPriceApi.price({
        customerId: form.getFieldValue('customerId') as number | undefined,
        productId: line.productId,
        unitId: line.unitId,
        quantity: line.quantity,
      });
      if (
        price.unitPrice !== null &&
        (overwrite || line.unitPrice === undefined || line.unitPrice === null)
      ) {
        form.setFieldValue(['lines', name, 'unitPrice'], price.unitPrice);
      }
      if (price.defaultDiscountPercent !== null && line.discountPercent === undefined) {
        form.setFieldValue(['lines', name, 'discountPercent'], price.defaultDiscountPercent);
      }
      if (price.taxRateId !== null && line.taxRateId === undefined) {
        form.setFieldValue(['lines', name, 'taxRateId'], price.taxRateId);
      }
      const parts = [
        price.unitPrice !== null
          ? `Giá ${formatMoney(price.unitPrice)} (${PRICE_SOURCES[price.priceSource ?? ''] ?? ''} ${
              price.priceListCode ?? ''
            })`
          : 'Chưa có bảng giá',
        price.floorPrice !== null ? `giá sàn ${formatMoney(price.floorPrice)}` : null,
      ].filter(Boolean);
      form.setFieldValue(['lines', name, 'priceHint'], parts.join(', '));
    } catch {
      form.setFieldValue(['lines', name, 'priceHint'], undefined);
    }
  };

  return (
    <Card
      size="small"
      style={{ marginBottom: 8 }}
      onKeyDown={(event) => {
        if (event.key === 'F8') {
          event.preventDefault();
          openPicker();
        }
      }}
    >
      <Form.Item name={[name, 'priceHint']} hidden>
        <Input />
      </Form.Item>
      <Form.Item name={[name, 'receiptTransactionId']} hidden>
        <Input />
      </Form.Item>
      <Form.Item name={[name, 'receiptNo']} hidden>
        <Input />
      </Form.Item>
      <Row gutter={8}>
        <Col xs={24} md={8}>
          <Form.Item
            name={[name, 'productId']}
            label="Hàng hóa / dịch vụ"
            rules={[{ required: true, message: 'Chọn hàng' }]}
          >
            <ProductSelect
              selectedLabel={form.getFieldValue(['lines', name, 'productLabel'])}
              onProduct={(product) => {
                clearReceipt();
                if (product) {
                  form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId);
                  form.setFieldValue(['lines', name, 'unitPrice'], undefined);
                  form.setFieldValue(['lines', name, 'taxRateId'], product.defaultTaxRateId ?? undefined);
                  void suggest(true);
                }
              }}
            />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item name={[name, 'unitId']} label="Đơn vị" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
            <Select options={options} onChange={() => void suggest(true)} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item
            name={[name, 'quantity']}
            label="Số lượng"
            rules={[{ required: true, message: 'Nhập SL' }]}
          >
            <DecimalInput min={0.001} onBlur={() => void suggest(false)} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name={[name, 'unitPrice']} label="Đơn giá" extra="Trống: theo bảng giá">
            <InputNumber<number> min={0} {...moneyInputProps} />
          </Form.Item>
        </Col>
        <Col xs={12} md={2}>
          <Form.Item name={[name, 'discountPercent']} label="CK %">
            <DecimalInput min={0} max={100} fractionDigits={2} />
          </Form.Item>
        </Col>
        <Col xs={12} md={3}>
          <Form.Item name={[name, 'taxRateId']} label="Thuế suất">
            <Select allowClear options={taxRates.options} />
          </Form.Item>
        </Col>
        <Col xs={10} md={1}>
          <Form.Item name={[name, 'promotional']} valuePropName="checked" label="Tặng">
            <Checkbox />
          </Form.Item>
        </Col>
        {withExpectedDate && (
          <Col xs={12} md={4}>
            <Form.Item name={[name, 'expectedDate']} label="Ngày giao dự kiến">
              <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        )}
        <Col xs={12} md={4}>
          <Form.Item
            name={[name, 'lotNo']}
            label="Lô chỉ định"
            tooltip="Trống: kho chọn lô hết hạn sớm nhất (FEFO). F8: chọn theo chứng từ nhập"
            extra={
              receiptNo && (
                <Tag closable onClose={clearReceipt} style={{ marginTop: 4 }}>
                  CT nhập: {receiptNo}
                </Tag>
              )
            }
          >
            <Input
              maxLength={50}
              onChange={clearReceipt}
              addonAfter={
                <Tooltip title="Chọn chứng từ nhập (F8)">
                  <FileSearchOutlined aria-label="Chọn chứng từ nhập" onClick={openPicker} />
                </Tooltip>
              }
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name={[name, 'lotOverrideReason']}
            label="Lý do chọn lô khác FEFO"
            tooltip="Bắt buộc khi lô chỉ định không phải lô hết hạn sớm nhất (BR-INV-21)"
          >
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={24} md={withExpectedDate ? 10 : 14}>
          <Form.Item
            name={[name, 'serialNumbers']}
            label="Serial chỉ định"
            tooltip="Hàng quản lý serial: gõ serial rồi Enter; trống thì kho chọn"
          >
            <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
          </Form.Item>
        </Col>
        <Col xs={22} md={23}>
          <Form.Item name={[name, 'description']} label="Ghi chú">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
        <Col xs={2} md={1}>
          <Button style={{ marginTop: 30 }} icon={<DeleteOutlined />} onClick={onRemove} />
        </Col>
      </Row>
      {hint && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {hint}
        </Typography.Text>
      )}
      {picker && product && productId && (
        <ReceiptSourcePicker
          branchId={picker.branchId}
          warehouseId={picker.warehouseId}
          productId={productId}
          productLabel={form.getFieldValue(['lines', name, 'productLabel']) as string | undefined}
          baseUnitName={unitName(product.baseUnitId) || ''}
          onCancel={() => setPicker(null)}
          onPick={(picks, reason) => {
            const lines = [...((form.getFieldValue('lines') as SalesLineForm[] | undefined) ?? [])];
            lines.splice(name, 1, ...linesFromReceipts(lines[name] ?? {}, picks, product.baseUnitId, reason));
            form.setFieldValue('lines', lines);
            setPicker(null);
          }}
        />
      )}
    </Card>
  );
}
