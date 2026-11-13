import { fireEvent, render, screen } from '@testing-library/react';
import { LineGrid, LineGridRow } from './LineGrid';

describe('LineGrid', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    // Bảng rộng 1200 trong khung 600: phải cuộn ngang.
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(1200);
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(600);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('chuột phải vào dòng: menu "Thêm dòng bên dưới" chèn ngay sau dòng đó', async () => {
    const onAdd = vi.fn();
    const columns = [{ title: 'Hàng hóa', width: '200px' }];
    render(
      <LineGrid columns={columns} minWidth={200} onAdd={onAdd}>
        <LineGridRow columns={columns} lineNo={1}>
          <span>dòng 1</span>
        </LineGridRow>
        <LineGridRow columns={columns} lineNo={2}>
          <span>dòng 2</span>
        </LineGridRow>
      </LineGrid>,
    );

    fireEvent.contextMenu(screen.getByText('dòng 1'));
    fireEvent.click(await screen.findByText('Thêm dòng bên dưới'));

    expect(onAdd).toHaveBeenCalledWith(1);
  });

  it('nút Thêm dòng thêm cuối bảng', () => {
    const onAdd = vi.fn();
    render(
      <LineGrid columns={[{ title: 'Hàng hóa', width: '200px' }]} minWidth={200} onAdd={onAdd}>
        <div />
      </LineGrid>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Thêm dòng' }));

    expect(onAdd).toHaveBeenCalledWith(undefined);
  });

  it('bảng rộng hơn khung: có thanh cuộn ngang dính đáy, kéo thanh thì bảng cuộn theo và ngược lại', () => {
    render(
      <LineGrid columns={[{ title: 'Hàng hóa', width: '1000px' }]} minWidth={1000} onAdd={() => {}}>
        <div>dòng</div>
      </LineGrid>,
    );
    const bar = screen.getByTestId('line-grid-hscroll');
    const body = bar.parentElement!.previousElementSibling as HTMLElement;

    bar.scrollLeft = 300;
    fireEvent.scroll(bar);
    expect(body.scrollLeft).toBe(300);

    body.scrollLeft = 120;
    fireEvent.scroll(body);
    expect(bar.scrollLeft).toBe(120);
  });
});
