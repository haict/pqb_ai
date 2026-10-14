import { DownloadOutlined, FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons';
import { App, Button, Dropdown, type MenuProps } from 'antd';
import { useState } from 'react';
import { saveBlob } from '../inventorySlip/slipContent';

export type ReportFormat = 'xlsx' | 'pdf';

const ITEMS: MenuProps['items'] = [
  { key: 'xlsx', icon: <FileExcelOutlined style={{ color: '#1d6f42' }} />, label: 'Excel (.xlsx)' },
  { key: 'pdf', icon: <FilePdfOutlined style={{ color: '#c4302b' }} />, label: 'PDF (.pdf)' },
];

/**
 * Tải báo cáo / sổ về máy dạng Excel, PDF; {@code build} nên import động thư viện dựng file (exceljs, pdfmake khá
 * nặng) để chỉ tải khi người dùng bấm.
 */
export function ReportDownloadButton({
  build,
  fileName,
}: {
  build: (format: ReportFormat) => Promise<Blob>;
  fileName: (format: ReportFormat) => string;
}) {
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);

  const download: MenuProps['onClick'] = async ({ key }) => {
    const format = key as ReportFormat;
    setBusy(true);
    try {
      saveBlob(await build(format), fileName(format));
    } catch {
      message.error('Không tạo được file, vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dropdown menu={{ items: ITEMS, onClick: download }} trigger={['click']} disabled={busy}>
      <Button icon={<DownloadOutlined />} loading={busy}>
        Tải về
      </Button>
    </Dropdown>
  );
}
