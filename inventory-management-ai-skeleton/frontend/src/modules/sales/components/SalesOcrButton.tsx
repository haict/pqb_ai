import { ScanOutlined } from '@ant-design/icons';
import { App, Button, Form, Tooltip, type FormInstance } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { aiApi, useAiHealth } from '../../ai/api';
import { OcrRecognizeModal } from '../../ai/components/OcrRecognizeModal';
import type { OcrLineValues } from '../../ai/ocrFill';
import type { SalesOcrResult } from '../../ai/types';
import { useCompanyId } from '../../masterdata/lookups';
import { salesOcrFill, type SalesOcrHeaderValues } from './salesOcrFill';

const DOCUMENT_TYPES = [{ value: 'SALES_ORDER' as const, label: 'Phiếu / đơn đặt hàng của khách' }];

/**
 * Nút "Nhận dạng chứng từ" trên form đơn bán (AI-2, D-64): ẩn khi thiếu quyền ocr:use, sales-order:create hoặc AI
 * không khả dụng (BR-AI-09). Điền ô còn trống, nối dòng hàng sau các dòng đã chọn hàng; không lưu.
 */
export function SalesOcrButton({ form, onNotes }: { form: FormInstance; onNotes(notes: string[]): void }) {
  const can = useCan();
  const { message } = App.useApp();
  const companyId = useCompanyId();
  const allowed = can('ocr:use') && can('sales-order:create');
  const health = useAiHealth(allowed);
  const [open, setOpen] = useState(false);
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const documentDate = Form.useWatch('documentDate', form) as Dayjs | undefined;
  if (!allowed || !health.ocrAvailable) {
    return null;
  }

  const apply = (result: SalesOcrResult) => {
    const fill = salesOcrFill(form.getFieldsValue(true) as SalesOcrHeaderValues, result);
    const kept = ((form.getFieldValue('lines') as OcrLineValues[] | undefined) ?? []).filter(
      (line) => line.productId,
    );
    form.setFieldsValue({ ...fill.header, lines: [...kept, ...fill.lines] });
    onNotes(fill.notes);
    const review = fill.lines.filter((line) => (line.ocrHints ?? []).length > 0).length;
    message.success(
      `Đã điền ${fill.lines.length} dòng từ chứng từ${review > 0 ? `, ${review} dòng cần kiểm tra` : ''}. ` +
        'Kiểm tra rồi bấm Lưu.',
    );
  };

  return (
    <>
      <Tooltip title={branchId ? undefined : 'Chọn chi nhánh trước'}>
        <Button icon={<ScanOutlined />} disabled={!branchId} onClick={() => setOpen(true)}>
          Nhận dạng chứng từ
        </Button>
      </Tooltip>
      <OcrRecognizeModal<SalesOcrResult>
        open={open}
        title="Nhận dạng phiếu đặt hàng của khách"
        documentTypes={DOCUMENT_TYPES}
        onRecognize={(file, documentType) =>
          aiApi.recognize<SalesOcrResult>(file, {
            companyId,
            branchId: branchId as number,
            documentType,
            documentDate: documentDate?.format('YYYY-MM-DD'),
          })
        }
        onDone={apply}
        onClose={() => setOpen(false)}
      />
    </>
  );
}
