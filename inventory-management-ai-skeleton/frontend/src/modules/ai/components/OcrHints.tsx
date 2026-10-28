import { Alert, Form, type FormInstance } from 'antd';

/** Ghi chú cả chứng từ sau khi nhận dạng (đối tác, tổng tiền, giá trị không điền); dùng chung form đơn mua, đơn bán. */
export function OcrNotes({ notes, onClose }: { notes: string[]; onClose(): void }) {
  if (notes.length === 0) {
    return null;
  }
  return (
    <Alert
      type="warning"
      showIcon
      closable
      onClose={onClose}
      style={{ margin: '8px 0' }}
      message="Thông tin từ chứng từ cần kiểm tra"
      description={
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      }
    />
  );
}

/** Điều cần kiểm tra của một dòng hàng điền từ chứng từ; đóng khi người dùng đã kiểm tra. */
export function OcrLineHints({ form, name }: { form: FormInstance; name: number }) {
  const hints = Form.useWatch(['lines', name, 'ocrHints'], { form, preserve: true }) as string[] | undefined;
  const level = Form.useWatch(['lines', name, 'ocrLevel'], { form, preserve: true }) as
    | 'warning'
    | 'error'
    | undefined;
  if (!hints || hints.length === 0) {
    return null;
  }
  return (
    <Alert
      type={level === 'error' ? 'error' : 'warning'}
      showIcon
      closable
      onClose={() => form.setFieldValue(['lines', name, 'ocrHints'], [])}
      style={{ marginTop: 4 }}
      message={
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {hints.map((hint) => (
            <li key={hint}>{hint}</li>
          ))}
        </ul>
      }
    />
  );
}
