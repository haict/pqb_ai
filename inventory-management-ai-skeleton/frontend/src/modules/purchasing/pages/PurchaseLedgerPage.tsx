import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, DatePicker, Form, Row, Select, Space, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/apiError';
import { PrintButton } from '../../../shared/components/PrintButton';
import { ReportDownloadButton, type ReportFormat } from '../../../shared/components/ReportDownloadButton';
import { SearchMultiSelect, type LabeledValue } from '../../../shared/components/SearchMultiSelect';
import { PERIOD_PRESETS, periodRange, type PeriodPreset } from '../../../shared/reportPeriod';
import { partnerApi, productApi } from '../../masterdata/api';
import { useCatalog, useCategories, useCompanyId } from '../../masterdata/lookups';
import { UserSelect } from '../../system/components/UserSelect';
import { useMyBranches } from '../../system/lookups';
import { purchaseLedgerApi } from '../api';
import { purchaseLedgerFileName } from '../ledger/purchaseLedgerContent';
import { PurchaseLedgerSheet } from '../ledger/PurchaseLedgerSheet';
import type { PurchaseLedger, PurchaseLedgerParams } from '../types';

/** Giới hạn của server (PurchaseLedgerService.MAX_SELECTED): số hàng / NCC chọn mỗi lần xem. */
const MAX_SELECTED = 200;

interface Filters {
  preset: PeriodPreset;
  range: [Dayjs, Dayjs];
  branchId?: number;
  purchaserId?: number;
  supplierGroupId?: number;
  productCategoryId?: number;
  products: LabeledValue[];
  suppliers: LabeledValue[];
}

/** Thư viện dựng file chỉ tải khi người dùng bấm (exceljs, pdfmake khá nặng). */
async function buildFile(ledger: PurchaseLedger, format: ReportFormat) {
  const exporter = await import('../ledger/purchaseLedgerExport');
  return format === 'xlsx' ? exporter.purchaseLedgerToExcel(ledger) : exporter.purchaseLedgerToPdf(ledger);
}

function initialFilters(branchId: number | undefined): Filters {
  const today = dayjs();
  return {
    preset: 'MONTH_TO_DATE',
    range: periodRange('MONTH_TO_DATE', today, [today, today]),
    branchId,
    products: [],
    suppliers: [],
  };
}

function toParams(companyId: number, filters: Filters): PurchaseLedgerParams {
  return {
    companyId,
    branchId: filters.branchId,
    dateFrom: filters.range[0].format('YYYY-MM-DD'),
    dateTo: filters.range[1].format('YYYY-MM-DD'),
    purchaserId: filters.purchaserId,
    supplierGroupId: filters.supplierGroupId,
    productCategoryId: filters.productCategoryId,
    productIds: filters.products.map((item) => item.value),
    supplierIds: filters.suppliers.map((item) => item.value),
  };
}

/**
 * Sổ chi tiết mua hàng (hiện trạng docs/HienTrang 14-15): chọn điều kiện như màn hình tham số, xem dòng đơn mua / trả
 * hàng / giảm giá đã ghi sổ kèm dòng tổng, bấm số chứng từ để mở chứng từ gốc; in, tải Excel / PDF.
 */
export function PurchaseLedgerPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const supplierGroups = useCatalog('partner-groups');
  const categories = useCategories();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<PurchaseLedgerParams | null>(null);

  const query = useQuery({
    queryKey: ['purchase-ledger', applied],
    queryFn: () => purchaseLedgerApi.get(applied as PurchaseLedgerParams),
    enabled: applied !== null,
  });
  const ledger = query.data;

  const changePreset = (preset: PeriodPreset) => {
    form.setFieldValue('range', periodRange(preset, dayjs(), form.getFieldValue('range')));
  };

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Sổ chi tiết mua hàng
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={initialFilters(branches.items[0]?.id)}
        onFinish={(values) => setApplied(toParams(companyId, values))}
      >
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="preset" label="Kỳ báo cáo">
              <Select
                onChange={changePreset}
                options={(Object.keys(PERIOD_PRESETS) as PeriodPreset[]).map((value) => ({
                  value,
                  label: PERIOD_PRESETS[value],
                }))}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="range"
              label="Từ ngày - Đến ngày"
              rules={[{ required: true, message: 'Chọn khoảng ngày' }]}
            >
              <DatePicker.RangePicker
                format="DD/MM/YYYY"
                allowClear={false}
                style={{ width: '100%' }}
                onChange={() => form.setFieldValue('preset', 'CUSTOM')}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn công ty" options={branches.options} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="productCategoryId" label="Nhóm VTHH">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Tất cả (gồm nhóm con)"
                options={categories.options}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="supplierGroupId" label="Nhóm NCC">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Tất cả"
                options={supplierGroups.options}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="purchaserId" label="NV mua hàng" tooltip="Người lập đơn mua / trả hàng mua">
              <UserSelect allowClear placeholder="Tất cả nhân viên" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="products"
              label="Hàng hóa"
              rules={[{ type: 'array', max: MAX_SELECTED, message: `Chọn tối đa ${MAX_SELECTED} hàng` }]}
            >
              <SearchMultiSelect
                queryKey="products"
                placeholder="Tất cả hàng hóa (tìm theo mã, tên để chọn)"
                fetch={(keyword) => productApi.list({ page: 0, size: 30, companyId, keyword })}
                toOption={(product) => ({ value: product.id, label: `${product.code} - ${product.name}` })}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="suppliers"
              label="Nhà cung cấp"
              rules={[
                { type: 'array', max: MAX_SELECTED, message: `Chọn tối đa ${MAX_SELECTED} nhà cung cấp` },
              ]}
            >
              <SearchMultiSelect
                queryKey="partners-SUPPLIER"
                placeholder="Tất cả nhà cung cấp (tìm theo mã, tên, MST)"
                fetch={(keyword) =>
                  partnerApi.list({ page: 0, size: 30, companyId, keyword, role: 'SUPPLIER' })
                }
                toOption={(partner) => ({ value: partner.id, label: `${partner.code} - ${partner.name}` })}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
            Xem sổ
          </Button>
          <Button onClick={() => form.resetFields()}>Xóa điều kiện</Button>
          {ledger && (
            <>
              <PrintButton label="In" title="Sổ chi tiết mua hàng">
                <PurchaseLedgerSheet ledger={ledger} />
              </PrintButton>
              <ReportDownloadButton
                build={(format) => buildFile(ledger, format)}
                fileName={(format) => purchaseLedgerFileName(ledger, format)}
              />
            </>
          )}
        </Space>
      </Form>
      {query.error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            query.error instanceof ApiError ? query.error.message : 'Không tải được sổ chi tiết mua hàng.'
          }
        />
      )}
      {ledger && (
        <>
          <PurchaseLedgerSheet ledger={ledger} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Số liệu lấy lúc {dayjs(ledger.generatedAt).format('HH:mm DD/MM/YYYY')} từ đơn mua, trả hàng, giảm
            giá hàng mua đã ghi sổ theo ngày hạch toán; chứng từ đã đảo có dòng âm theo ngày đảo. NV mua hàng
            là người lập chứng từ.
          </Typography.Text>
        </>
      )}
    </Card>
  );
}
