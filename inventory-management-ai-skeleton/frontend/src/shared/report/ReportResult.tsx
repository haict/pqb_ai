import { Alert, Empty, Spin } from 'antd';
import { ApiError } from '../api/apiError';
import type { TableReport } from './tableReport';
import { TableReportSheet } from './TableReportSheet';

/** Vùng kết quả báo cáo: đang tải, lỗi từ server (thông báo tiếng Việt), chưa xem, hoặc bảng báo cáo. */
export function ReportResult({
  loading,
  error,
  report,
}: {
  loading: boolean;
  error: unknown;
  report: TableReport | null;
}) {
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message={error instanceof ApiError ? error.message : 'Không tải được báo cáo.'}
      />
    );
  }
  if (loading) {
    return <Spin />;
  }
  if (!report) {
    return <Empty description="Chọn điều kiện rồi bấm Xem báo cáo" />;
  }
  return <TableReportSheet report={report} />;
}
