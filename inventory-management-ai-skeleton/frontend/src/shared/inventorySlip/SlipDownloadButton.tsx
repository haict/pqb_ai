import { DownloadOutlined, FileExcelOutlined, FilePdfOutlined, FileWordOutlined } from '@ant-design/icons';
import { App, Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { useState } from 'react';
import { toApiError } from '../api/apiError';
import { saveBlob, slipFileName, type InventorySlipView } from './slipContent';

type Format = 'xlsx' | 'docx' | 'pdf';

/** Thư viện dựng file chỉ tải khi người dùng bấm (exceljs, docx, pdfmake khá nặng). */
const BUILDERS: Record<Format, (slip: InventorySlipView) => Promise<Blob>> = {
  xlsx: async (slip) => (await import('./slipExcel')).slipToExcel(slip),
  docx: async (slip) => (await import('./slipWord')).slipToWord(slip),
  pdf: async (slip) => (await import('./slipPdf')).slipToPdf(slip),
};

const ITEMS: MenuProps['items'] = [
  { key: 'xlsx', icon: <FileExcelOutlined style={{ color: '#1d6f42' }} />, label: 'Excel (.xlsx)' },
  { key: 'docx', icon: <FileWordOutlined style={{ color: '#2b579a' }} />, label: 'Word (.docx)' },
  { key: 'pdf', icon: <FilePdfOutlined style={{ color: '#c4302b' }} />, label: 'PDF (.pdf)' },
];

/**
 * Tải phiếu nhập / xuất kho về máy dưới dạng Excel, Word, PDF; nội dung giống bản in. Dữ liệu phiếu chỉ tải khi bấm
 * ({@code load}) để mở màn hình chi tiết không gọi API in phiếu.
 */
export function SlipDownloadButton({ load }: { load(): Promise<InventorySlipView> }) {
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);

  const download: MenuProps['onClick'] = async ({ key }) => {
    const format = key as Format;
    setBusy(true);
    try {
      const slip = await load();
      saveBlob(await BUILDERS[format](slip), slipFileName(slip, format));
    } catch (error) {
      message.error(toApiError(error).message || 'Không tạo được file, vui lòng thử lại.');
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
