import { PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { Button, Tooltip, Typography, theme } from 'antd';
import { useRef, type KeyboardEventHandler, type ReactNode } from 'react';
import { focusLastRow } from './lineGridFocus';
import { LINE_NO_EXTRA, lineGridStyle, type LineGridColumn } from './lineGridStyles';

/** Chiều cao thanh nút "Thêm dòng" dính đáy: đệm 8 + nút nhỏ 24 + đệm 8. */
const ADD_BAR_HEIGHT = 40;

/**
 * Bảng dòng chứng từ trong form: tiêu đề "Hàng hóa" và các nút bên phải trên một hàng; tên cột hiện một lần; mỗi
 * dòng một hàng, cuộn ngang khi màn hình hẹp hơn {@code minWidth}; nút thêm dòng ngay dưới dòng cuối, dính đáy
 * màn hình khi danh sách dài. Thêm dòng thì cuộn về cột đầu, đưa dòng mới vào tầm nhìn và đặt con trỏ vào ô đầu tiên để nhập ngay.
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
  const scrollRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const { token } = theme.useToken();

  const add = () => {
    onAdd();
    // Đợi Form.List vẽ dòng mới rồi mới cuộn / đặt con trỏ.
    window.setTimeout(() => focusLastRow(scrollRef.current, bodyRef.current), 0);
  };

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
        <Typography.Text strong>Hàng hóa</Typography.Text>
        {actions}
      </div>
      <div ref={scrollRef} style={{ overflowX: 'auto' }}>
        <div ref={bodyRef} style={{ minWidth: minWidth + LINE_NO_EXTRA }}>
          <div
            style={{
              ...lineGridStyle(columns),
              padding: '4px 0',
              borderBottom: '1px solid rgba(5, 5, 5, 0.06)',
              fontWeight: 500,
            }}
          >
            <span style={{ textAlign: 'center' }}>STT</span>
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
      {/* Dính đáy vùng cuộn: danh sách dài vẫn thấy nút mà không phải cuộn xuống cuối. */}
      <div
        style={{
          position: 'sticky',
          bottom: 0,
          zIndex: 2,
          padding: '8px 0',
          background: token.colorBgContainer,
        }}
      >
        <Button type="dashed" size="small" icon={<PlusOutlined />} aria-label="Thêm dòng" onClick={add}>
          Thêm dòng
        </Button>
      </div>
    </>
  );
}

/**
 * Một dòng của bảng: số thứ tự ({@code lineNo}, đếm từ 1 theo vị trí dòng), các ô theo cột, phần dưới
 * ({@code below}) cho gợi ý giá, cảnh báo OCR...
 */
export function LineGridRow({
  columns,
  lineNo,
  children,
  below,
  onKeyDown,
}: {
  columns: LineGridColumn[];
  lineNo: number;
  children: ReactNode;
  below?: ReactNode;
  onKeyDown?: KeyboardEventHandler<HTMLDivElement>;
}) {
  return (
    <div
      // Chừa chỗ cho thanh "Thêm dòng" dính đáy để dòng vừa thêm không bị che khi cuộn tới.
      style={{ padding: '4px 0', borderBottom: '1px solid rgba(5, 5, 5, 0.06)', scrollMarginBottom: ADD_BAR_HEIGHT + 8 }}
      onKeyDown={onKeyDown}
    >
      <div style={lineGridStyle(columns)}>
        <Typography.Text type="secondary" style={{ lineHeight: '32px', textAlign: 'center' }}>
          {lineNo}
        </Typography.Text>
        {children}
      </div>
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
