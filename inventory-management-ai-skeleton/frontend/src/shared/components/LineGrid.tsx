import { PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { Button, Dropdown, Tooltip, Typography, theme } from 'antd';
import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEventHandler,
  type ReactNode,
} from 'react';
import { focusRow } from './lineGridFocus';
import { LINE_NO_EXTRA, lineGridStyle, type LineGridColumn } from './lineGridStyles';

/** Chiều cao thanh dính đáy: đệm 4 + thanh cuộn ngang ~12 + 4 + nút nhỏ 24 + đệm 8. */
const ADD_BAR_HEIGHT = 52;
/** Lớp CSS (styles.css) giữ cố định cột STT và cột đầu tiên khi kéo ngang. */
const CELLS_CLASS = 'line-grid-cells';

/** Chèn dòng mới tại vị trí {@code index} (0 = đầu bảng) do LineGrid cung cấp cho từng dòng (menu chuột phải). */
const InsertLineContext = createContext<((index: number) => void) | null>(null);

/**
 * Bảng dòng chứng từ trong form: tiêu đề "Hàng hóa" và các nút bên phải trên một hàng; tên cột hiện một lần; mỗi
 * dòng một hàng, cuộn ngang khi màn hình hẹp hơn {@code minWidth}, cột STT và cột đầu tiên (hàng hóa) đứng yên khi
 * kéo ngang; nút thêm dòng ngay dưới dòng cuối, dính đáy màn hình khi danh sách dài; chuột phải vào một dòng để thêm
 * dòng ngay dưới dòng đó. Thêm dòng thì cuộn về cột đầu, đưa dòng mới vào tầm nhìn và đặt con trỏ vào ô đầu tiên.
 *
 * {@code onAdd(index)}: {@code index} là vị trí chèn (truyền thẳng cho {@code Form.List add(value, index)});
 * không có {@code index} thì thêm cuối bảng.
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
  onAdd(index?: number): void;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const barRef = useRef<HTMLDivElement>(null);
  const { token } = theme.useToken();
  const [scrollWidth, setScrollWidth] = useState(0);
  const [clientWidth, setClientWidth] = useState(0);
  const hasStickyBar = scrollWidth > clientWidth;

  // Theo dõi bề rộng bảng / khung để hiện thanh cuộn ngang dính đáy chỉ khi bảng rộng hơn khung.
  useEffect(() => {
    const scroller = scrollRef.current;
    const body = bodyRef.current;
    if (!scroller || !body || typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const measure = () => {
      setScrollWidth(scroller.scrollWidth);
      setClientWidth(scroller.clientWidth);
    };
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    observer.observe(body);
    measure();
    return () => observer.disconnect();
  }, []);

  // Đồng bộ hai chiều; so sánh trước khi gán để không lặp sự kiện scroll.
  const syncFromBody = () => {
    const bar = barRef.current;
    const scroller = scrollRef.current;
    if (bar && scroller && bar.scrollLeft !== scroller.scrollLeft) {
      bar.scrollLeft = scroller.scrollLeft;
    }
  };
  const syncFromBar = () => {
    const bar = barRef.current;
    const scroller = scrollRef.current;
    if (bar && scroller && scroller.scrollLeft !== bar.scrollLeft) {
      scroller.scrollLeft = bar.scrollLeft;
    }
  };

  const add = (index?: number) => {
    onAdd(index);
    // Đợi Form.List vẽ dòng mới rồi mới cuộn / đặt con trỏ.
    window.setTimeout(() => focusRow(scrollRef.current, bodyRef.current, index), 0);
  };

  const gridVars = {
    '--line-grid-bg': token.colorBgContainer,
    '--line-grid-second-left': `${LINE_NO_EXTRA}px`,
  } as CSSProperties;

  return (
    <InsertLineContext.Provider value={add}>
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
      {/* Khi có thanh cuộn ngang dính đáy thì ẩn thanh cuộn gốc ở cuối bảng để không hiện hai thanh. */}
      <div
        ref={scrollRef}
        style={{ overflowX: 'auto', scrollbarWidth: hasStickyBar ? 'none' : undefined }}
        onScroll={syncFromBody}
      >
        <div ref={bodyRef} style={{ ...gridVars, minWidth: minWidth + LINE_NO_EXTRA }}>
          <div
            className={CELLS_CLASS}
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
      {/* Dính đáy vùng cuộn: danh sách dài vẫn thấy nút và thanh cuộn ngang mà không phải cuộn xuống cuối. */}
      <div
        style={{
          position: 'sticky',
          bottom: 0,
          zIndex: 2,
          padding: '4px 0 8px',
          background: token.colorBgContainer,
        }}
      >
        {hasStickyBar && (
          <div
            ref={barRef}
            data-testid="line-grid-hscroll"
            style={{ overflowX: 'auto', overflowY: 'hidden', marginBottom: 4 }}
            onScroll={syncFromBar}
          >
            <div style={{ width: scrollWidth, height: 1 }} />
          </div>
        )}
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          aria-label="Thêm dòng"
          onClick={() => add()}
        >
          Thêm dòng
        </Button>
      </div>
    </InsertLineContext.Provider>
  );
}

/**
 * Một dòng của bảng: số thứ tự ({@code lineNo}, đếm từ 1 theo vị trí dòng), các ô theo cột, phần dưới
 * ({@code below}) cho gợi ý giá, cảnh báo OCR... Chuột phải vào dòng mở menu "Thêm dòng bên dưới".
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
  const insertLine = useContext(InsertLineContext);
  const row = (
    <div
      // Chừa chỗ cho thanh "Thêm dòng" dính đáy để dòng vừa thêm không bị che khi cuộn tới.
      style={{
        padding: '4px 0',
        borderBottom: '1px solid rgba(5, 5, 5, 0.06)',
        scrollMarginBottom: ADD_BAR_HEIGHT + 8,
      }}
      onKeyDown={onKeyDown}
    >
      <div className={CELLS_CLASS} style={lineGridStyle(columns)}>
        <Typography.Text type="secondary" style={{ lineHeight: '32px', textAlign: 'center' }}>
          {lineNo}
        </Typography.Text>
        {children}
      </div>
      {below}
    </div>
  );
  if (!insertLine) {
    return row;
  }
  return (
    <Dropdown
      trigger={['contextMenu']}
      menu={{
        items: [{ key: 'insert-below', icon: <PlusOutlined />, label: 'Thêm dòng bên dưới' }],
        // lineNo đếm từ 1 nên cũng là vị trí chèn ngay sau dòng này.
        onClick: () => insertLine(lineNo),
      }}
    >
      {row}
    </Dropdown>
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
