import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { SearchMultiSelect, type LabeledValue } from '../../../shared/components/SearchMultiSelect';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { productApi } from '../../masterdata/api';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { orderReportApi } from '../api';
import { stockBacklogReport } from '../orderReports';

/** Giới hạn của server (SalesOrderReportService.MAX_PRODUCTS). */
const MAX_PRODUCTS = 200;

interface Filters {
  warehouseId?: number;
  products: LabeledValue[];
}

type Params = Parameters<typeof orderReportApi.stockBacklog>[0];

/** Tồn kho so với đặt hàng chưa giao (BR-RPT-26, H-06): hàng thiếu để giao các đơn đã duyệt chưa xuất kho. */
export function StockBacklogPage() {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<Params | null>(null);
  const query = useQuery({
    queryKey: ['stock-backlog', applied],
    queryFn: () => orderReportApi.stockBacklog(applied as Params),
    enabled: applied !== null,
  });
  const report = useMemo(() => (query.data ? stockBacklogReport(query.data) : null), [query.data]);

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Tồn so với đặt hàng chưa giao
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ products: [] }}
        onFinish={(values) =>
          setApplied({
            companyId,
            warehouseId: values.warehouseId,
            productIds: values.products.map((item) => item.value),
          })
        }
      >
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="warehouseId" label="Kho">
              <Select allowClear placeholder="Mọi kho trong phạm vi" options={warehouses.options} />
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
                placeholder="Tất cả hàng có đơn chưa giao"
                fetch={(keyword) => productApi.list({ page: 0, size: 30, companyId, keyword })}
                toOption={(product) => ({ value: product.id, label: `${product.code} - ${product.name}` })}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
            Xem báo cáo
          </Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      <ReportResult loading={query.isFetching} error={query.error} report={report} />
    </Card>
  );
}
