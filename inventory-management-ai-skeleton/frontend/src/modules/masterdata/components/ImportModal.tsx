import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Alert, App, Button, List, Modal, Space, Typography, Upload } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { ApiError, toApiError } from '../../../shared/api/apiError';
import { useIdempotencyKey } from '../../../shared/api/useIdempotencyKey';
import type { ImportResult } from '../types';

interface Props<R extends ImportResult> {
  open: boolean;
  title: string;
  onDownloadTemplate(): Promise<void>;
  onImport(file: File, idempotencyKey: string): Promise<R>;
  onDone(result: R): void;
  onClose(): void;
  /** Trường nhập thêm (ví dụ ngày chứng từ) hiển thị trước ô chọn file. */
  children?: ReactNode;
  successMessage?(result: R): string;
}

/**
 * Nhập danh mục từ Excel (BR-UX-05): tải mẫu, chọn file .xlsx, nhập. Server kiểm tra mọi dòng; có lỗi thì không ghi dòng
 * nào và trả lỗi theo dòng để hiển thị ở đây. Cùng một file trong một lần mở giữ cùng Idempotency-Key.
 */
export function ImportModal<R extends ImportResult = ImportResult>({
  open,
  title,
  onDownloadTemplate,
  onImport,
  onDone,
  onClose,
  children,
  successMessage,
}: Props<R>) {
  const { message } = App.useApp();
  const { key, renew } = useIdempotencyKey();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      renew();
      setFile(null);
      setError(null);
    }
  }, [open, renew]);

  const submit = async () => {
    if (!file) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await onImport(file, key);
      message.success(successMessage ? successMessage(result) : `Đã nhập ${result.created} dòng.`);
      onDone(result);
      onClose();
    } catch (err) {
      setError(toApiError(err));
      renew();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      okText="Nhập"
      cancelText="Đóng"
      okButtonProps={{ disabled: !file, loading: submitting }}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
      width={640}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Nhập theo file mẫu. Hệ thống kiểm tra toàn bộ file; có lỗi thì chưa nhập dòng nào.
        </Typography.Paragraph>
        <Button icon={<DownloadOutlined />} onClick={() => onDownloadTemplate()}>
          Tải file mẫu
        </Button>
        {children}
        <Upload
          accept=".xlsx"
          maxCount={1}
          beforeUpload={(selected) => {
            setFile(selected);
            setError(null);
            renew();
            return false;
          }}
          onRemove={() => setFile(null)}
          fileList={file ? [{ uid: '1', name: file.name, status: 'done' }] : []}
        >
          <Button icon={<UploadOutlined />}>Chọn file Excel (.xlsx)</Button>
        </Upload>
        {error && (
          <Alert
            type="error"
            showIcon
            message={error.message}
            description={
              error.fieldErrors.length > 0 && (
                <List
                  size="small"
                  style={{ maxHeight: 240, overflow: 'auto' }}
                  dataSource={error.fieldErrors}
                  renderItem={(fieldError) => <List.Item>{fieldError.message}</List.Item>}
                />
              )
            }
          />
        )}
      </Space>
    </Modal>
  );
}
