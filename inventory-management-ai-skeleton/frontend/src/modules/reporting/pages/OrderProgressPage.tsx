import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { statusLabel } from '../../inventory/labels';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { orderReportApi } from '../api';
import { orderProgressReport } from '../orderReports';

const STATUSES = ['DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'POSTED', 'CANCELLED', 'REVERSED'];

interface Filters extends PeriodValues {
  branchId?: number;
  partnerId?: number;
  status?: string;
}

type Params = Parameters<typeof orderReportApi.purchaseProgress>[0] & { partnerId?: number };

/**
 * Tình hình thực hiện đơn mua / đơn bán (BR-RPT-25, 26): trạng thái, hẹn nhận / giao, trả lại, đã thanh toán / thu,
 * còn nợ; bấm số đơn mở đơn.
 */
export function OrderProgressPage({ kind }: { kind: 'purchase' | 'sales' }) {
  const sales = kind === 'sales';
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<Params | null>(null);
  const query = useQuery({
    queryKey: ['order-progress', kind, applied],
    queryFn: () => {
      const { partnerId, ...params } = applied as Params;
      return sales
        ? orderReportApi.salesProgress({ ...params, customerId: partnerId })
        : orderReportApi.purchaseProgress({ ...params, supplierId: partnerId });
    },
    enabled: applied !== null,
  });
  const report = useMemo(() => (query.data ? orderProgressReport(query.data) : null), [query.data]);

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        {sales ? 'Tình hình thực hiện đơn bán' : 'Tình hình thực hiện đơn mua'}
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ ...initialPeriod(), branchId: branches.items[0]?.id }}
        onFinish={(values) =>
          setApplied({
            companyId,
            branchId: values.branchId,
            partnerId: values.partnerId,
            status: values.status,
            ...rangeParams(values.range),
          })
        }
      >
        <Row gutter={16}>
          <PeriodFields />
          <Col xs={24} md={10}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn công ty" options={branches.options} />
            </Form.Item>
          </Col>
          <Col xs={24} md={14}>
            <Form.Item name="partnerId" label={sales ? 'Khách hàng' : 'Nhà cung cấp'}>
              <PartnerSelect role={sales ? 'CUSTOMER' : 'SUPPLIER'} allowClear />
            </Form.Item>
          </Col>
          <Col xs={24} md={10}>
            <Form.Item name="status" label="Trạng thái">
              <Select
                allowClear
                placeholder="Mọi trạng thái (trừ đã hủy)"
                options={STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
              />
            </Form.Item>
          </Col>
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
            Xem báo cáo
          </Button>
          <Button onClick={() => form.resetFields()}>Xóa điều kiện</Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      <ReportResult loading={query.isFetching} error={query.error} report={report} />
    </Card>
  );
}
