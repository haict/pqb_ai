import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { SearchMultiSelect, type LabeledValue } from '../../../shared/components/SearchMultiSelect';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { productApi } from '../../masterdata/api';
import { useCategories, useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { inventoryReportApi } from '../api';
import { movementReport } from '../inventoryReports';

/** Giới hạn của server (StockLedgerReportService.MAX_PRODUCTS). */
const MAX_PRODUCTS = 200;

interface Filters extends PeriodValues {
  warehouseId?: number;
  productCategoryId?: number;
  products: LabeledValue[];
}

type Params = Parameters<typeof inventoryReportApi.movement>[0];

/** Tổng hợp nhập - xuất - tồn theo kỳ, số lượng (BR-RPT-22); giá trị có từ Giai đoạn 8. */
export function InventoryMovementPage() {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const categories = useCategories();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<Params | null>(null);
  const movement = useQuery({
    queryKey: ['inventory-movement', applied],
    queryFn: () => inventoryReportApi.movement(applied as Params),
    enabled: applied !== null,
  });
  const report = useMemo(() => (movement.data ? movementReport(movement.data) : null), [movement.data]);

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Nhập - xuất - tồn
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ ...initialPeriod(), products: [] }}
        onFinish={(values) =>
          setApplied({
            companyId,
            warehouseId: values.warehouseId,
            productCategoryId: values.productCategoryId,
            productIds: values.products.map((item) => item.value),
            ...rangeParams(values.range),
          })
        }
      >
        <Row gutter={16}>
          <PeriodFields />
          <Col xs={24} md={10}>
            <Form.Item name="warehouseId" label="Kho">
              <Select allowClear placeholder="Mọi kho trong phạm vi" options={warehouses.options} />
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
          <Col xs={24} md={16}>
            <Form.Item
              name="products"
              label="Hàng hóa"
              rules={[{ type: 'array', max: MAX_PRODUCTS, message: `Chọn tối đa ${MAX_PRODUCTS} hàng` }]}
            >
              <SearchMultiSelect
                queryKey="products"
                placeholder="Tất cả hàng hóa (tìm theo mã, tên để chọn)"
                fetch={(keyword) => productApi.list({ page: 0, size: 30, companyId, keyword })}
                toOption={(product) => ({ value: product.id, label: `${product.code} - ${product.name}` })}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={movement.isFetching}>
            Xem báo cáo
          </Button>
          <Button onClick={() => form.resetFields()}>Xóa điều kiện</Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      <ReportResult loading={movement.isFetching} error={movement.error} report={report} />
    </Card>
  );
}
