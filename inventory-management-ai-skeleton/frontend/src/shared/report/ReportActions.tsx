import { PrintButton } from '../components/PrintButton';
import { ReportDownloadButton } from '../components/ReportDownloadButton';
import type { TableReport } from './tableReport';
import { TableReportSheet } from './TableReportSheet';

/** Nút In và Tải về (Excel, PDF) cho một báo cáo bảng; thư viện dựng file chỉ tải khi bấm. */
export function ReportActions({ report }: { report: TableReport }) {
  return (
    <>
      <PrintButton label="In" title={report.title}>
        <TableReportSheet report={report} />
      </PrintButton>
      <ReportDownloadButton
        build={async (format) => {
          const exporter = await import('./tableReportExport');
          return format === 'xlsx' ? exporter.tableReportToExcel(report) : exporter.tableReportToPdf(report);
        }}
        fileName={(format) => `${report.fileName}.${format}`}
      />
    </>
  );
}
