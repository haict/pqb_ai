import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Form, Row, Select, Space, Tabs, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { SearchMultiSelect, type LabeledValue } from '../../../shared/components/SearchMultiSelect';
import { PeriodFields } from '../../../shared/report/PeriodFields';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import type { TableReport } from '../../../shared/report/tableReport';
import { initialPeriod, rangeParams, type PeriodValues } from '../../../shared/reportPeriod';
import { productApi } from '../../masterdata/api';
import { PartnerSelect } from '../../masterdata/components/EntitySelects';
import { useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { orderReportApi } from '../api';
import { priceHistoryReport, supplierDefectsReport } from '../orderReports';
import { BranchCol } from '../../../shared/components/BranchCol';

/** Giới hạn của server (PurchaseReportService.MAX_PRODUCTS). */
const MAX_PRODUCTS = 200;

type Kind = 'prices' | 'defects';

interface Filters extends PeriodValues {
  branchId?: number;
  supplierId?: number;
  products: LabeledValue[];
}

interface Applied {
  kind: Kind;
  branchId?: number;
  supplierId?: number;
  productIds: number[];
  dateFrom: string;
  dateTo: string;
}

/** Lịch sử giá mua và hàng lỗi theo nhà cung cấp (BR-RPT-25). */
export function PurchaseAnalysisPage() {
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const [form] = Form.useForm<Filters>();
  const [kind, setKind] = useState<Kind>('prices');
  const [applied, setApplied] = useState<Applied | null>(null);
  const query = useQuery({
    queryKey: ['purchase-analysis', companyId, applied],
    queryFn: async (): Promise<TableReport> => {
      const target = applied as Applied;
      const params = {
        companyId,
        branchId: target.branchId,
        dateFrom: target.dateFrom,
        dateTo: target.dateTo,
      };
      return target.kind === 'prices'
        ? priceHistoryReport(
            await orderReportApi.priceHistory({
              ...params,
              supplierId: target.supplierId,
              productIds: target.productIds,
            }),
          )
        : supplierDefectsReport(await orderReportApi.supplierDefects(params));
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
        Giá mua, hàng lỗi theo nhà cung cấp
      </Typography.Title>
      <Tabs
        activeKey={kind}
        onChange={(key) => setKind(key as Kind)}
        items={[
          { key: 'prices', label: 'Lịch sử giá mua' },
          { key: 'defects', label: 'Hàng lỗi theo NCC' },
        ]}
      />
      <Form<Filters>
        form={form}
        layout="vertical"
        initialValues={{ ...initialPeriod('YEAR_TO_DATE'), products: [], branchId: branches.filterDefaultId }}
        onFinish={(values) =>
          setApplied({
            kind,
            branchId: values.branchId,
            supplierId: values.supplierId,
            productIds: values.products.map((item) => item.value),
            ...rangeParams(values.range),
          })
        }
      >
        <Row gutter={16}>
          <PeriodFields />
          <BranchCol xs={24} md={10}>
            <Form.Item name="branchId" label="Đơn vị">
              <Select allowClear placeholder="Toàn công ty" options={branches.options} />
            </Form.Item>
          </BranchCol>
          {kind === 'prices' && (
            <>
              <Col xs={24} md={10}>
                <Form.Item name="supplierId" label="Nhà cung cấp">
                  <PartnerSelect role="SUPPLIER" allowClear />
                </Form.Item>
              </Col>
              <Col xs={24} md={14}>
                <Form.Item
                  name="products"
                  label="Hàng hóa"
                  rules={[{ type: 'array', max: MAX_PRODUCTS, message: `Chọn tối đa ${MAX_PRODUCTS} hàng` }]}
                >
                  <SearchMultiSelect
                    queryKey="products"
                    placeholder="Tất cả hàng hóa (tìm theo mã, tên để chọn)"
                    fetch={(keyword) => productApi.list({ page: 0, size: 30, companyId, keyword })}
                    toOption={(product) => ({
                      value: product.id,
                      label: `${product.code} - ${product.name}`,
                    })}
                  />
                </Form.Item>
              </Col>
            </>
          )}
        </Row>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={query.isFetching}>
            Xem báo cáo
          </Button>
          {report && <ReportActions report={report} />}
        </Space>
      </Form>
      <ReportResult
        loading={query.isFetching}
        error={applied?.kind === kind ? query.error : null}
        report={report}
      />
    </Card>
  );
}
