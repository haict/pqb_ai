import { DeleteOutlined, FileSearchOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  type FormInstance,
} from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { moneyInputProps } from '../../finance/components/debtInputs';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import {
  useCachedSuggestedWarehouse,
  useSuggestedWarehouse,
  useTaxRates,
  useWarehouses,
} from '../../masterdata/lookups';
import { useProductUnits } from '../../purchasing/components/productUnits';
import { salesOrderApi, salesPriceApi } from '../api';
import { PRICE_SOURCES } from '../labels';
import { ReceiptSourcePicker } from './ReceiptSourcePicker';
import { linesFromReceipts, type SalesLineForm } from './salesLines';
import { DecimalInput } from '../../../shared/components/DecimalInput';
import { LineGrid, LineGridRow } from '../../../shared/components/LineGrid';
import { lineCellStyle, type LineGridColumn } from '../../../shared/components/lineGridStyles';
import { OcrLineHints } from '../../ai/components/OcrHints';
import { useFinancialPosting } from '../../../shared/ui/postingMode';
import { DATE_INPUT_FORMAT } from '../../../shared/dateInput';

/**
 * Danh sách dòng đơn bán trong form (tên trường {@code lines}): giá, chiết khấu, thuế, lô / serial chỉ định. F8 trên
 * một dòng (hoặc nút "Chọn CT nhập") mở danh sách chứng từ nhập còn tồn tại kho xuất của dòng để xuất đích danh
 * (D-63); form cần có {@code branchId}. Kho xuất theo từng dòng, tự gợi ý theo tên nhóm hàng (D-76). Dòng hiện dạng bảng, mỗi dòng một hàng; {@code actions}: nút đặt cùng hàng
 * tiêu đề (nhập Excel, nhận dạng chứng từ).
 */
export function SalesLinesEditor<T extends { lines?: SalesLineForm[]; customerId?: number }>({
  form,
  withExpectedDate,
  actions,
}: {
  form: FormInstance<T>;
  withExpectedDate?: boolean;
  actions?: ReactNode;
}) {
  const columns = lineColumns(withExpectedDate);
  return (
    <Form.List name="lines">
      {(fields, { add, remove }) => (
        <LineGrid
          columns={columns}
          minWidth={withExpectedDate ? 2000 : 1870}
          onAdd={() => add({ quantity: 1, unitPrice: 0 })}
          actions={actions}
        >
          {fields.map((field) => (
            <SalesLineFields
              key={field.key}
              form={form as unknown as FormInstance<{ lines?: SalesLineForm[]; customerId?: number }>}
              name={field.name}
              columns={columns}
              withExpectedDate={withExpectedDate}
              onRemove={() => remove(field.name)}
            />
          ))}
        </LineGrid>
      )}
    </Form.List>
  );
}

function lineColumns(withExpectedDate?: boolean): LineGridColumn[] {
  return [
    { title: 'Hàng hóa / dịch vụ', width: '260px' },
    { title: 'Kho xuất', width: '170px', tooltip: 'Tự gợi ý theo tên nhóm hàng; sửa được' },
    { title: 'Đơn vị', width: '100px' },
    { title: 'Số lượng', width: '100px' },
    { title: 'Đơn giá', width: '130px', tooltip: 'Trống: theo bảng giá' },
    { title: 'CK %', width: '70px' },
    { title: 'Thuế suất', width: '110px' },
    { title: 'Tặng', width: '50px' },
    ...(withExpectedDate ? [{ title: 'Ngày giao dự kiến', width: '125px' }] : []),
    {
      title: 'Lô chỉ định',
      width: '170px',
      tooltip: 'Trống: kho chọn lô hết hạn sớm nhất (FEFO). F8: chọn theo chứng từ nhập',
    },
    {
      title: 'Hạn dùng',
      width: '110px',
      tooltip: 'Theo lô trong kho: tự điền khi chọn chứng từ nhập (F8) hoặc khi nhập lô còn tồn tại kho xuất',
    },
    {
      title: 'Lý do khác FEFO',
      width: '180px',
      tooltip: 'Ghi khi chỉ định lô không phải lô hết hạn sớm nhất, không bắt buộc (BR-INV-21)',
    },
    {
      title: 'Serial chỉ định',
      width: '180px',
      tooltip: 'Hàng quản lý serial: gõ serial rồi Enter; trống thì kho chọn',
    },
    { title: 'Ghi chú', width: 'minmax(140px, 1fr)' },
  ];
}

function SalesLineFields({
  form,
  name,
  columns,
  withExpectedDate,
  onRemove,
}: {
  form: FormInstance<{ lines?: SalesLineForm[]; customerId?: number; branchId?: number }>;
  name: number;
  columns: LineGridColumn[];
  withExpectedDate?: boolean;
  onRemove(): void;
}) {
  const { message } = App.useApp();
  const taxRates = useTaxRates();
  const financial = useFinancialPosting();
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const hint = Form.useWatch(['lines', name, 'priceHint'], form) as string | undefined;
  const receiptNo = Form.useWatch(['lines', name, 'receiptNo'], form) as string | undefined;
  const { options, product, unitName } = useProductUnits(productId);
  const [picker, setPicker] = useState<{ branchId: number; warehouseId: number } | null>(null);
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const service = product?.productType === 'SERVICE';
  const warehouses = useWarehouses();
  const suggested = useSuggestedWarehouse(service ? undefined : branchId, productId);
  const cachedSuggestion = useCachedSuggestedWarehouse();
  const warehouseOptions = warehouses.items
    .filter((warehouse) => warehouse.branchId === branchId && warehouse.warehouseType === 'NORMAL')
    .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} - ${warehouse.name}` }));
  useEffect(() => {
    // D-76: kho trống thì điền kho gợi ý theo nhóm hàng (nhập tay, Excel, nhận dạng chứng từ); không đè kho đã chọn.
    if (suggested && !form.getFieldValue(['lines', name, 'warehouseId'])) {
      form.setFieldValue(['lines', name, 'warehouseId'], suggested);
    }
  }, [form, name, productId, suggested]);
  useEffect(() => {
    // ĐVT trống khi đã có hàng (nhận dạng chứng từ không đọc / không khớp ĐVT, nhập Excel): điền ĐVT cơ sở của hàng.
    if (product && product.id === productId && !form.getFieldValue(['lines', name, 'unitId'])) {
      form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId);
    }
  }, [form, name, product, productId]);
  const lineWarehouse = () => form.getFieldValue(['lines', name, 'warehouseId']) as number | undefined;

  const clearReceipt = () => {
    form.setFieldValue(['lines', name, 'receiptTransactionId'], undefined);
    form.setFieldValue(['lines', name, 'receiptNo'], undefined);
    form.setFieldValue(['lines', name, 'expiryDate'], undefined);
  };

  /** Lô gõ tay: lấy hạn dùng của lô từ danh sách lô còn tồn tại kho xuất (cùng nguồn với F8). */
  const lookupExpiry = async () => {
    const lotNo = (form.getFieldValue(['lines', name, 'lotNo']) as string | undefined)?.trim();
    const warehouseId = lineWarehouse();
    if (!lotNo || !productId || !branchId || !warehouseId) {
      return;
    }
    try {
      const sources = await salesOrderApi.receiptSources({ branchId, warehouseId, productId });
      const lot = sources.find((source) => source.lotNo === lotNo);
      form.setFieldValue(['lines', name, 'expiryDate'], lot?.expiryDate ?? undefined);
    } catch {
      form.setFieldValue(['lines', name, 'expiryDate'], undefined);
    }
  };

  /** F8: chọn chứng từ nhập còn tồn của hàng tại kho xuất của dòng (D-63, D-76). */
  const openPicker = () => {
    const warehouseId = lineWarehouse();
    if (!productId || !product) {
      message.info('Chọn hàng trước khi nhấn F8.');
    } else if (!branchId || !warehouseId) {
      message.info('Chọn chi nhánh và kho xuất của dòng trước khi nhấn F8.');
    } else if (!product.lotTracked && !product.serialTracked) {
      message.info('Hàng không quản lý lô / serial: kho xuất theo FEFO khi ghi sổ.');
    } else {
      setPicker({ branchId, warehouseId });
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
      // D-73: chế độ chỉ quản lý kho không lấy giá bảng giá, đơn giá trống = 0.
      if (financial && price.unitPrice !== null && (overwrite || !line.unitPrice)) {
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
      form.setFieldValue(['lines', name, 'priceHint'], financial ? parts.join(', ') : undefined);
    } catch {
      form.setFieldValue(['lines', name, 'priceHint'], undefined);
    }
  };

  return (
    <LineGridRow
      columns={columns}
      lineNo={name + 1}
      onKeyDown={(event) => {
        if (event.key === 'F8') {
          event.preventDefault();
          openPicker();
        }
      }}
      below={
        <>
          <Form.Item name={[name, 'priceHint']} hidden>
            <Input />
          </Form.Item>
          <Form.Item name={[name, 'receiptTransactionId']} hidden>
            <Input />
          </Form.Item>
          <Form.Item name={[name, 'receiptNo']} hidden>
            <Input />
          </Form.Item>
          {(hint || receiptNo) && (
            <Space size={8} style={{ marginTop: 2 }}>
              {hint && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {hint}
                </Typography.Text>
              )}
              {receiptNo && (
                <Tag closable onClose={clearReceipt}>
                  CT nhập: {receiptNo}
                </Tag>
              )}
            </Space>
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
                lines.splice(
                  name,
                  1,
                  ...linesFromReceipts(lines[name] ?? {}, picks, product.baseUnitId, reason),
                );
                form.setFieldValue('lines', lines);
                setPicker(null);
              }}
            />
          )}
          <OcrLineHints form={form} name={name} />
        </>
      }
    >
      <Form.Item
        name={[name, 'productId']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Chọn hàng' }]}
      >
        <ProductSelect
          creatable
          selectedLabel={form.getFieldValue(['lines', name, 'productLabel'])}
          onProduct={(product) => {
            clearReceipt();
            // Đổi hàng: kho theo gợi ý của hàng mới (không có gợi ý thì để người dùng chọn).
            form.setFieldValue(
              ['lines', name, 'warehouseId'],
              product && product.productType !== 'SERVICE' ? cachedSuggestion(branchId, product.id) : undefined,
            );
            if (product) {
              form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId);
              form.setFieldValue(['lines', name, 'unitPrice'], 0);
              form.setFieldValue(['lines', name, 'taxRateId'], product.defaultTaxRateId ?? undefined);
              void suggest(true);
            }
          }}
        />
      </Form.Item>
      {service ? (
        <Input disabled value="—" style={lineCellStyle} aria-label="Không áp dụng" />
      ) : (
        <Form.Item
          name={[name, 'warehouseId']}
          style={lineCellStyle}
          rules={[{ required: true, message: 'Chọn kho' }]}
        >
          <Select showSearch optionFilterProp="label" options={warehouseOptions} onChange={clearReceipt} />
        </Form.Item>
      )}
      <Form.Item
        name={[name, 'unitId']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Chọn ĐVT' }]}
      >
        <Select options={options} onChange={() => void suggest(true)} />
      </Form.Item>
      <Form.Item
        name={[name, 'quantity']}
        style={lineCellStyle}
        rules={[{ required: true, message: 'Nhập SL' }]}
      >
        <DecimalInput min={0.001} onBlur={() => void suggest(false)} />
      </Form.Item>
      <Form.Item name={[name, 'unitPrice']} style={lineCellStyle}>
        <InputNumber<number> min={0} placeholder={financial ? 'Theo bảng giá' : '0'} {...moneyInputProps} />
      </Form.Item>
      <Form.Item name={[name, 'discountPercent']} style={lineCellStyle}>
        <DecimalInput min={0} max={100} fractionDigits={2} />
      </Form.Item>
      <Form.Item name={[name, 'taxRateId']} style={lineCellStyle}>
        <Select allowClear options={taxRates.options} />
      </Form.Item>
      <Form.Item
        name={[name, 'promotional']}
        valuePropName="checked"
        style={{ ...lineCellStyle, textAlign: 'center' }}
      >
        <Checkbox aria-label="Hàng tặng" />
      </Form.Item>
      {withExpectedDate && (
        <Form.Item name={[name, 'expectedDate']} style={lineCellStyle}>
          <DatePicker format={DATE_INPUT_FORMAT} style={{ width: '100%' }} />
        </Form.Item>
      )}
      <Form.Item name={[name, 'lotNo']} style={lineCellStyle}>
        <Input
          maxLength={50}
          onChange={clearReceipt}
          onBlur={() => void lookupExpiry()}
          addonAfter={
            <Tooltip title="Chọn chứng từ nhập (F8)">
              <FileSearchOutlined aria-label="Chọn chứng từ nhập" onClick={openPicker} />
            </Tooltip>
          }
        />
      </Form.Item>
      <Form.Item name={[name, 'expiryDate']} style={lineCellStyle}>
        <ExpiryText />
      </Form.Item>
      <Form.Item name={[name, 'lotOverrideReason']} style={lineCellStyle}>
        <Input maxLength={500} />
      </Form.Item>
      <Form.Item name={[name, 'serialNumbers']} style={lineCellStyle}>
        <Select mode="tags" tokenSeparators={[',', ' ']} open={false} />
      </Form.Item>
      <Form.Item name={[name, 'description']} style={lineCellStyle}>
        <Input maxLength={500} />
      </Form.Item>
      <Tooltip title="Xóa dòng">
        <Button icon={<DeleteOutlined />} aria-label="Xóa dòng" onClick={onRemove} />
      </Tooltip>
    </LineGridRow>
  );
}

/** Hạn dùng chỉ đọc của dòng (giá trị do form cấp). */
function ExpiryText({ value }: { value?: string }) {
  return <Input readOnly tabIndex={-1} value={value ? formatDate(value) : ''} aria-label="Hạn dùng" />;
}
