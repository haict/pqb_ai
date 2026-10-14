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
import { salesLedgerApi } from '../api';
import { LEDGER_GROUPINGS } from '../labels';
import { ledgerFileName } from '../ledger/ledgerContent';
import { SalesLedgerSheet } from '../ledger/SalesLedgerSheet';
import type { SalesLedger, SalesLedgerGrouping, SalesLedgerParams } from '../types';

/** Giới hạn của server (SalesLedgerService.MAX_SELECTED): số hàng / khách chọn mỗi lần xem. */
const MAX_SELECTED = 200;

interface Filters {
  groupBy: SalesLedgerGrouping;
  preset: PeriodPreset;
  range: [Dayjs, Dayjs];
  branchId?: number;
  salesUserId?: number;
  customerGroupId?: number;
  productCategoryId?: number;
  products: LabeledValue[];
  customers: LabeledValue[];
}

/** Thư viện dựng file chỉ tải khi người dùng bấm (exceljs, pdfmake khá nặng). */
async function buildFile(ledger: SalesLedger, format: ReportFormat) {
  return format === 'xlsx'
    ? (await import('../ledger/ledgerExcel')).ledgerToExcel(ledger)
    : (await import('../ledger/ledgerPdf')).ledgerToPdf(ledger);
}

function initialFilters(branchId: number | undefined): Filters {
  const today = dayjs();
  return {
    groupBy: 'NONE',
    preset: 'MONTH_TO_DATE',
    range: periodRange('MONTH_TO_DATE', today, [today, today]),
    branchId,
    products: [],
    customers: [],
  };
}

function toParams(companyId: number, filters: Filters): SalesLedgerParams {
  return {
    companyId,
    branchId: filters.branchId,
    dateFrom: filters.range[0].format('YYYY-MM-DD'),
    dateTo: filters.range[1].format('YYYY-MM-DD'),
    salesUserId: filters.salesUserId,
    customerGroupId: filters.customerGroupId,
    productCategoryId: filters.productCategoryId,
    productIds: filters.products.map((item) => item.value),
    customerIds: filters.customers.map((item) => item.value),
    groupBy: filters.groupBy,
  };
}

/**
 * Sổ chi tiết bán hàng (mẫu S16-DNN, BR-RPT-26): chọn điều kiện như màn hình tham số hiện trạng, xem sổ theo từng
 * hàng hóa, bấm số chứng từ để mở chứng từ gốc; in, tải Excel / PDF. Lọc và tổng hợp ở server.
 */
export function SalesLedgerPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const customerGroups = useCatalog('partner-groups');
  const categories = useCategories();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<SalesLedgerParams | null>(null);

  const ledger = useQuery({
    queryKey: ['sales-ledger', applied],
    queryFn: () => salesLedgerApi.get(applied as SalesLedgerParams),
    enabled: applied !== null,
  });
  const data = ledger.data;

  const changePreset = (preset: PeriodPreset) => {
    form.setFieldValue('range', periodRange(preset, dayjs(), form.getFieldValue('range')));
  };

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Sổ chi tiết bán hàng
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={initialFilters(branches.items[0]?.id)}
        onFinish={(values) => setApplied(toParams(companyId, values))}
      >
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="groupBy" label="Thống kê theo">
              <Select
                options={(Object.keys(LEDGER_GROUPINGS) as SalesLedgerGrouping[]).map((value) => ({
                  value,
                  label: LEDGER_GROUPINGS[value],
                }))}
              />
            </Form.Item>
          </Col>
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
          <Col xs={24} md={6}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn công ty" options={branches.options} />
            </Form.Item>
          </Col>
          <Col xs={24} md={6}>
            <Form.Item name="salesUserId" label="Nhân viên">
              <UserSelect allowClear placeholder="Tất cả nhân viên" />
            </Form.Item>
          </Col>
          <Col xs={24} md={6}>
            <Form.Item name="customerGroupId" label="Nhóm KH">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Tất cả"
                options={customerGroups.options}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={6}>
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
              name="customers"
              label="Khách hàng"
              rules={[{ type: 'array', max: MAX_SELECTED, message: `Chọn tối đa ${MAX_SELECTED} khách` }]}
            >
              <SearchMultiSelect
                queryKey="partners-CUSTOMER"
                placeholder="Tất cả khách hàng (tìm theo mã, tên, MST)"
                fetch={(keyword) =>
                  partnerApi.list({ page: 0, size: 30, companyId, keyword, role: 'CUSTOMER' })
                }
                toOption={(partner) => ({ value: partner.id, label: `${partner.code} - ${partner.name}` })}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={ledger.isFetching}>
            Xem sổ
          </Button>
          <Button onClick={() => form.resetFields()}>Xóa điều kiện</Button>
          {data && (
            <>
              <PrintButton label="In" title="Sổ chi tiết bán hàng">
                <SalesLedgerSheet ledger={data} />
              </PrintButton>
              <ReportDownloadButton
                build={(format) => buildFile(data, format)}
                fileName={(format) => ledgerFileName(data, format)}
              />
            </>
          )}
        </Space>
      </Form>
      {ledger.error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            ledger.error instanceof ApiError ? ledger.error.message : 'Không tải được sổ chi tiết bán hàng.'
          }
        />
      )}
      {data && (
        <>
          <SalesLedgerSheet ledger={data} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Số liệu lấy lúc {dayjs(data.generatedAt).format('HH:mm DD/MM/YYYY')} từ đơn bán, trả lại, giảm giá
            đã ghi sổ theo ngày hạch toán; chứng từ đã đảo có dòng âm theo ngày đảo.
          </Typography.Text>
        </>
      )}
    </Card>
  );
}
