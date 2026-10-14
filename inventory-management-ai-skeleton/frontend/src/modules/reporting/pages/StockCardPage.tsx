import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { stockApi } from '../../inventory/api';
import { ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { inventoryReportApi } from '../api';
import { stockCardReport } from '../inventoryReports';

interface Filters extends PeriodValues {
  warehouseId?: number;
  productId?: number;
  lotId?: number;
  serialId?: number;
}

type Params = Parameters<typeof inventoryReportApi.stockCard>[0];

/** Thẻ kho theo hàng, lô; lịch sử serial (BR-RPT-21). Bấm số chứng từ mở phiếu kho. */
export function StockCardPage() {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [form] = Form.useForm<Filters>();
  const productId = Form.useWatch('productId', form);
  const [applied, setApplied] = useState<Params | null>(null);

  const lots = useQuery({
    queryKey: ['stock-card', 'lots', productId],
    queryFn: () => stockApi.lots({ page: 0, size: 200, companyId, productId }),
    enabled: productId !== undefined,
  });
  const serials = useQuery({
    queryKey: ['stock-card', 'serials', productId],
    queryFn: () => stockApi.serials({ page: 0, size: 200, companyId, productId }),
    enabled: productId !== undefined,
  });
  const card = useQuery({
    queryKey: ['stock-card', applied],
    queryFn: () => inventoryReportApi.stockCard(applied as Params),
    enabled: applied !== null,
  });
  const report = useMemo(() => (card.data ? stockCardReport(card.data) : null), [card.data]);

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Thẻ kho
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={initialPeriod()}
        onFinish={(values) =>
          setApplied({
            companyId,
            warehouseId: values.warehouseId,
            productId: values.productId,
            lotId: values.lotId,
            serialId: values.serialId,
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
          <Col xs={24} md={10}>
            <Form.Item
              name="productId"
              label="Hàng hóa"
              rules={[{ required: true, message: 'Chọn hàng hóa' }]}
            >
              <ProductSelect
                allowClear
                onChange={() => form.setFieldsValue({ lotId: undefined, serialId: undefined })}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={7}>
            <Form.Item name="lotId" label="Số lô">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Mọi lô"
                loading={lots.isFetching}
                options={(lots.data?.items ?? []).map((lot) => ({ value: lot.id, label: lot.lotNo }))}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={7}>
            <Form.Item name="serialId" label="Serial (lịch sử serial)">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Mọi serial"
                loading={serials.isFetching}
                options={(serials.data?.items ?? []).map((serial) => ({
                  value: serial.id,
                  label: serial.serialNo,
                }))}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={card.isFetching}>
            Xem báo cáo
          </Button>
          <Button onClick={() => form.resetFields()}>Xóa điều kiện</Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      <ReportResult loading={card.isFetching} error={card.error} report={report} />
    </Card>
  );
}
