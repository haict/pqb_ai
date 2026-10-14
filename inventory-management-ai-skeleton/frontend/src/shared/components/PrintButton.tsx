import { PrinterOutlined } from '@ant-design/icons';
import { Button, Modal } from 'antd';
import { useState, type ReactNode } from 'react';

/** Nút in: mở hộp xem trước, chỉ in vùng nội dung (BR-SAL-04, BR-SAL-22, BR-PUR-08). */
export function PrintButton({
  label,
  title,
  children,
}: {
  label: string;
  title: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button icon={<PrinterOutlined />} onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Modal
        open={open}
        title={title}
        width={900}
        okText="In"
        okButtonProps={{ icon: <PrinterOutlined /> }}
        cancelText="Đóng"
        onOk={() => window.print()}
        onCancel={() => setOpen(false)}
        destroyOnHidden
      >
        <div className="print-area">{children}</div>
      </Modal>
    </>
  );
}
