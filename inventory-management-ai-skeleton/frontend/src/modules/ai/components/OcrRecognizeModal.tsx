import { ScanOutlined, UploadOutlined } from '@ant-design/icons';
import { Alert, Button, Modal, Radio, Space, Typography, Upload } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError, toApiError } from '../../../shared/api/apiError';
import type { OcrDocumentType } from '../types';

const ACCEPT = '.jpg,.jpeg,.png,.pdf';
/** Bằng ai.ocr.max-file-size của server; chỉ kiểm tra sớm cho tiện, server vẫn kiểm tra lại. */
const MAX_FILE_BYTES = 10 * 1024 * 1024;

interface Props<R> {
  open: boolean;
  title: string;
  documentTypes: { value: OcrDocumentType; label: string }[];
  onRecognize(file: File, documentType: OcrDocumentType): Promise<R>;
  onDone(result: R): void;
  onClose(): void;
}

/**
 * Chọn ảnh / PDF chứng từ, xem trước ngay trên máy (chưa gửi đi), bấm "Nhận dạng" để lấy gợi ý điền form (D-64).
 * Không lưu gì: người dùng kiểm tra form rồi tự bấm Lưu (BR-AI-04).
 */
export function OcrRecognizeModal<R>({ open, title, documentTypes, onRecognize, onDone, onClose }: Props<R>) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [documentType, setDocumentType] = useState<OcrDocumentType | undefined>(documentTypes[0]?.value);
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setFile(null);
      setError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!file) {
      setPreview(null);
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const submit = async () => {
    if (!file || !documentType) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      onDone(await onRecognize(file, documentType));
      onClose();
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      okText="Nhận dạng"
      cancelText="Đóng"
      okButtonProps={{ disabled: !file, loading: submitting, icon: <ScanOutlined /> }}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
      width={720}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Hệ thống chỉ điền vào các ô còn trống và thêm dòng hàng; không tự lưu. Kiểm tra các ô được đánh dấu rồi
          bấm Lưu. File không được lưu lại sau khi nhận dạng.
        </Typography.Paragraph>
        <Radio.Group
          value={documentType}
          onChange={(event) => setDocumentType(event.target.value as OcrDocumentType)}
          options={documentTypes}
          optionType="button"
        />
        <Upload
          accept={ACCEPT}
          maxCount={1}
          beforeUpload={(selected) => {
            setError(null);
            if (selected.size > MAX_FILE_BYTES) {
              setError(new ApiError('File vượt dung lượng cho phép (10 MB).', 'OCR_FILE_TOO_LARGE', null));
              return false;
            }
            setFile(selected);
            return false;
          }}
          onRemove={() => setFile(null)}
          fileList={file ? [{ uid: '1', name: file.name, status: 'done' }] : []}
        >
          <Button icon={<UploadOutlined />}>Chọn ảnh hoặc PDF</Button>
        </Upload>
        {preview && file && (
          <div style={{ border: '1px solid #f0f0f0', maxHeight: 360, overflow: 'auto', textAlign: 'center' }}>
            {file.type === 'application/pdf' ? (
              <iframe title="Xem trước chứng từ" src={preview} style={{ width: '100%', height: 340, border: 0 }} />
            ) : (
              <img alt="Xem trước chứng từ" src={preview} style={{ maxWidth: '100%' }} />
            )}
          </div>
        )}
        {submitting && (
          <Typography.Text type="secondary">Đang nhận dạng, thường mất 5–20 giây mỗi trang...</Typography.Text>
        )}
        {error && <Alert type="error" showIcon message={error.message} />}
      </Space>
    </Modal>
  );
}
