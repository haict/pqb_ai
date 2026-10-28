import { PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { Button, Space, Tooltip, Typography } from 'antd';
import type { KeyboardEventHandler, ReactNode } from 'react';
import { lineGridStyle, type LineGridColumn } from './lineGridStyles';

/**
 * Bảng dòng chứng từ trong form: tiêu đề "Hàng hóa" + nút thêm dòng (chỉ biểu tượng) và các nút bên phải trên một
 * hàng; tên cột hiện một lần; mỗi dòng một hàng, cuộn ngang khi màn hình hẹp hơn {@code minWidth}.
 */
export function LineGrid({
  columns,
  minWidth,
  onAdd,
  actions,
  children,
}: {
  columns: LineGridColumn[];
  minWidth: number;
  onAdd(): void;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          margin: '8px 0',
        }}
      >
        <Space>
          <Typography.Text strong>Hàng hóa</Typography.Text>
          <Tooltip title="Thêm dòng">
            <Button size="small" icon={<PlusOutlined />} aria-label="Thêm dòng" onClick={onAdd} />
          </Tooltip>
        </Space>
        {actions}
      </div>
      <div style={{ overflowX: 'auto' }}>
        <div style={{ minWidth }}>
          <div
            style={{
              ...lineGridStyle(columns),
              padding: '4px 0',
              borderBottom: '1px solid rgba(5, 5, 5, 0.06)',
              fontWeight: 500,
            }}
          >
            {columns.map((column) => (
              <span key={column.title}>
                {column.title}
                {column.tooltip && (
                  <Tooltip title={column.tooltip}>
                    <QuestionCircleOutlined style={{ marginLeft: 4, color: 'rgba(0, 0, 0, 0.45)' }} />
                  </Tooltip>
                )}
              </span>
            ))}
            <span />
          </div>
          {children}
        </div>
      </div>
    </>
  );
}

/** Một dòng của bảng: các ô theo cột, phần dưới ({@code below}) cho gợi ý giá, cảnh báo OCR... */
export function LineGridRow({
  columns,
  children,
  below,
  onKeyDown,
}: {
  columns: LineGridColumn[];
  children: ReactNode;
  below?: ReactNode;
  onKeyDown?: KeyboardEventHandler<HTMLDivElement>;
}) {
  return (
    <div style={{ padding: '4px 0', borderBottom: '1px solid rgba(5, 5, 5, 0.06)' }} onKeyDown={onKeyDown}>
      <div style={lineGridStyle(columns)}>{children}</div>
      {below}
    </div>
  );
}

/** Ô của cột không áp dụng cho dòng (hàng không quản lý lô / serial, dịch vụ). */
export function NotApplicable() {
  return (
    <Typography.Text type="secondary" style={{ lineHeight: '32px', textAlign: 'center' }}>
      –
    </Typography.Text>
  );
}
