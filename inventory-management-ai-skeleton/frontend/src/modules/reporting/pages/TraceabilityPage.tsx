import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Tabs, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import type { TableReport } from '../../../shared/report/tableReport';
import { stockApi } from '../../inventory/api';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { inventoryReportApi } from '../api';
import { customerTraceReport, lotTraceReport } from '../inventoryReports';

type Kind = 'lot' | 'customer';

interface LotFilters {
  productId?: number;
  lotId?: number;
  serialId?: number;
}

interface CustomerFilters extends PeriodValues {
  customerId?: number;
}

type Applied =
  | { kind: 'lot'; lotId?: number; serialId?: number }
  | { kind: 'customer'; customerId: number; dateFrom: string; dateTo: string };

/** Truy xuất lô / serial → nơi nhập, khách nhận; khách → lô, serial đã nhận (BR-RPT-24, hỗ trợ thu hồi BR-INV-42). */
export function TraceabilityPage() {
  const companyId = useCompanyId();
  const [kind, setKind] = useState<Kind>('lot');
  const [lotForm] = Form.useForm<LotFilters>();
  const [customerForm] = Form.useForm<CustomerFilters>();
  const productId = Form.useWatch('productId', lotForm);
  const [applied, setApplied] = useState<Applied | null>(null);

  const lots = useQuery({
    queryKey: ['traceability', 'lots', productId],
    queryFn: () => stockApi.lots({ page: 0, size: 200, companyId, productId }),
    enabled: productId !== undefined,
  });
  const serials = useQuery({
    queryKey: ['traceability', 'serials', productId],
    queryFn: () => stockApi.serials({ page: 0, size: 200, companyId, productId }),
    enabled: productId !== undefined,
  });
  const query = useQuery({
    queryKey: ['traceability', companyId, applied],
    queryFn: async (): Promise<TableReport> => {
      const target = applied as Applied;
      if (target.kind === 'lot') {
        return lotTraceReport(
          await inventoryReportApi.lotTrace({ companyId, lotId: target.lotId, serialId: target.serialId }),
        );
      }
      return customerTraceReport(
        await inventoryReportApi.customerTrace({
          companyId,
          customerId: target.customerId,
          dateFrom: target.dateFrom,
          dateTo: target.dateTo,
        }),
      );
    },
    enabled: applied !== null,
  });
  const report = useMemo(
    () => (applied?.kind === kind ? (query.data ?? null) : null),
    [applied, kind, query.data],
  );

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Truy xuất lô / serial
      </Typography.Title>
      <Tabs
        activeKey={kind}
        onChange={(key) => setKind(key as Kind)}
        items={[
          { key: 'lot', label: 'Theo lô / serial' },
          { key: 'customer', label: 'Theo khách hàng' },
        ]}
      />
      {kind === 'lot' ? (
        <Form<LotFilters>
          form={lotForm}
          layout="vertical"
          onFinish={(values) => setApplied({ kind: 'lot', lotId: values.lotId, serialId: values.serialId })}
        >
          <Row gutter={16}>
            <Col xs={24} md={10}>
              <Form.Item
                name="productId"
                label="Hàng hóa"
                rules={[{ required: true, message: 'Chọn hàng hóa' }]}
              >
                <ProductSelect
                  allowClear
                  onChange={() => lotForm.setFieldsValue({ lotId: undefined, serialId: undefined })}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={7}>
              <Form.Item
                name="lotId"
                label="Số lô"
                dependencies={['serialId']}
                rules={[
                  ({ getFieldValue }) => ({
                    validator: (_, value) =>
                      (value === undefined) !== (getFieldValue('serialId') === undefined)
                        ? Promise.resolve()
                        : Promise.reject(new Error('Chọn một lô hoặc một serial')),
                  }),
                ]}
              >
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  loading={lots.isFetching}
                  options={(lots.data?.items ?? []).map((lot) => ({ value: lot.id, label: lot.lotNo }))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={7}>
              <Form.Item name="serialId" label="Serial">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
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
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
              Xem báo cáo
            </Button>
            {report && <ReportActions report={report} />}
          </Space>
        </Form>
      ) : (
        <Form<CustomerFilters>
          form={customerForm}
          layout="vertical"
          initialValues={initialPeriod('YEAR_TO_DATE')}
          onFinish={(values) =>
            values.customerId !== undefined &&
            setApplied({ kind: 'customer', customerId: values.customerId, ...rangeParams(values.range) })
          }
        >
          <Row gutter={16}>
            <PeriodFields />
            <Col xs={24} md={10}>
              <Form.Item
                name="customerId"
                label="Khách hàng"
                rules={[{ required: true, message: 'Chọn khách hàng' }]}
              >
                <PartnerSelect role="CUSTOMER" allowClear />
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
      )}
      <ReportResult
        loading={query.isFetching}
        error={applied?.kind === kind ? query.error : null}
        report={report}
      />
    </Card>
  );
}
