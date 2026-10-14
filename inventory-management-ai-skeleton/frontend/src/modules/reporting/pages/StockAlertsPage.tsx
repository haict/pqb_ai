import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, InputNumber, Select, Space, Tabs, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { ReportActions } from '../../../shared/report/ReportActions';
import { ReportResult } from '../../../shared/report/ReportResult';
import type { TableReport } from '../../../shared/report/tableReport';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { inventoryReportApi } from '../api';
import { exceptionsReport, missingDocumentsReport, slowMovingReport } from '../inventoryReports';

type Kind = 'slow' | 'exceptions' | 'missing';

interface Applied {
  kind: Kind;
  warehouseId?: number;
  days?: number;
}

/**
 * Hàng cần xử lý (BR-RPT-23): tồn lâu (mặc định theo tham số số ngày), hàng ngoại lệ (hết hạn, chất lượng khác ĐẠT,
 * khóa), hàng thiếu hồ sơ bắt buộc.
 */
export function StockAlertsPage() {
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [kind, setKind] = useState<Kind>('slow');
  const [warehouseId, setWarehouseId] = useState<number>();
  const [days, setDays] = useState<number | null>(null);
  const [applied, setApplied] = useState<Applied | null>(null);

  const query = useQuery({
    queryKey: ['stock-alerts', companyId, applied],
    queryFn: async (): Promise<TableReport> => {
      const target = applied as Applied;
      const params = { companyId, warehouseId: target.warehouseId };
      if (target.kind === 'slow') {
        return slowMovingReport(await inventoryReportApi.slowMoving({ ...params, days: target.days }));
      }
      if (target.kind === 'exceptions') {
        return exceptionsReport(await inventoryReportApi.exceptions(params));
      }
      return missingDocumentsReport(await inventoryReportApi.missingDocuments(params));
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
        Hàng tồn lâu, ngoại lệ, thiếu hồ sơ
      </Typography.Title>
      <Tabs
        activeKey={kind}
        onChange={(key) => setKind(key as Kind)}
        items={[
          { key: 'slow', label: 'Hàng tồn lâu' },
          { key: 'exceptions', label: 'Hàng ngoại lệ' },
          { key: 'missing', label: 'Hàng thiếu hồ sơ' },
        ]}
      />
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          style={{ width: 260 }}
          placeholder="Mọi kho trong phạm vi"
          value={warehouseId}
          onChange={setWarehouseId}
          options={warehouses.options}
        />
        {kind === 'slow' && (
          <InputNumber
            min={1}
            max={3650}
            placeholder="Số ngày (theo tham số)"
            style={{ width: 200 }}
            value={days}
            onChange={setDays}
          />
        )}
        <Button
          type="primary"
          icon={<SearchOutlined />}
          loading={query.isFetching}
          onClick={() => setApplied({ kind, warehouseId, days: days ?? undefined })}
        >
          Xem báo cáo
        </Button>
        {report && <ReportActions report={report} />}
      </Space>
      <ReportResult
        loading={query.isFetching}
        error={applied?.kind === kind ? query.error : null}
        report={report}
      />
    </Card>
  );
}
