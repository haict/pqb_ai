import { ScanOutlined } from '@ant-design/icons';
import { App, Button, Form, Tooltip, type FormInstance } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { aiApi, useAiHealth } from '../../ai/api';
import { OcrRecognizeModal } from '../../ai/components/OcrRecognizeModal';
import type { PurchaseOcrResult } from '../../ai/types';
import { purchaseOcrFill, type OcrHeaderValues, type OcrLineValues } from './purchaseOcrFill';

const DOCUMENT_TYPES = [
  { value: 'PURCHASE_INVOICE' as const, label: 'Hóa đơn của nhà cung cấp' },
  { value: 'PURCHASE_ORDER' as const, label: 'Đơn đặt hàng gửi nhà cung cấp' },
];

/**
 * Nút "Nhận dạng chứng từ" trên form đơn mua (D-64): ẩn khi thiếu quyền ocr:use hoặc AI không khả dụng (BR-AI-09).
 * Điền ô còn trống, nối dòng hàng sau các dòng đã chọn hàng; không lưu.
 */
export function PurchaseOcrButton({
  form,
  companyId,
  onNotes,
}: {
  form: FormInstance;
  companyId: number;
  onNotes(notes: string[]): void;
}) {
  const can = useCan();
  const { message } = App.useApp();
  const allowed = can('ocr:use') && can('purchase-order:create');
  const health = useAiHealth(allowed);
  const [open, setOpen] = useState(false);
  const branchId = Form.useWatch('branchId', form) as number | undefined;
  const documentDate = Form.useWatch('documentDate', form) as Dayjs | undefined;
  if (!allowed || !health.ocrAvailable) {
    return null;
  }

  const apply = (result: PurchaseOcrResult) => {
    const fill = purchaseOcrFill(form.getFieldsValue(true) as OcrHeaderValues, result);
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
      <OcrRecognizeModal<PurchaseOcrResult>
        open={open}
        title="Nhận dạng hóa đơn / đơn đặt hàng của nhà cung cấp"
        documentTypes={DOCUMENT_TYPES}
        onRecognize={(file, documentType) =>
          aiApi.recognize<PurchaseOcrResult>(file, {
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
