import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { orderReportApi } from '../api';
import { salesJournalReport } from '../orderReports';

interface Filters extends PeriodValues {
  branchId?: number;
}

type Params = Parameters<typeof orderReportApi.salesJournal>[0];

/** Sổ nhật ký bán hàng theo chứng từ bán đã ghi sổ (BR-RPT-26). */
export function SalesJournalPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [form] = Form.useForm<Filters>();
  const [applied, setApplied] = useState<Params | null>(null);
  const query = useQuery({
    queryKey: ['sales-journal', applied],
    queryFn: () => orderReportApi.salesJournal(applied as Params),
    enabled: applied !== null,
  });
  const report = useMemo(() => (query.data ? salesJournalReport(query.data) : null), [query.data]);

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Sổ nhật ký bán hàng
      </Typography.Title>
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ ...initialPeriod(), branchId: branches.items[0]?.id }}
        onFinish={(values) =>
          setApplied({ companyId, branchId: values.branchId, ...rangeParams(values.range) })
        }
      >
        <Row gutter={16}>
          <PeriodFields />
          <Col xs={24} md={10}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn công ty" options={branches.options} />
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
