import { focusLastRow } from './lineGridFocus';

function grid() {
  const scroller = document.createElement('div');
  scroller.innerHTML = `
    <div id="body">
      <div><input id="r1" /></div>
      <div><input id="r2-disabled" disabled /><input id="r2" /><input id="r2-other" /></div>
    </div>`;
  document.body.replaceChildren(scroller);
  scroller.scrollLeft = 300;
  return { scroller, body: scroller.querySelector<HTMLElement>('#body') };
}

describe('focusLastRow', () => {
  it('thêm dòng: cuộn ngang về đầu, đặt con trỏ vào ô nhập đầu tiên của dòng mới', () => {
    const { scroller, body } = grid();
    const scrollIntoView = vi.fn();
    (body!.lastElementChild as HTMLElement).scrollIntoView = scrollIntoView;

    focusLastRow(scroller, body);

    expect(document.activeElement?.id).toBe('r2');
    expect(scroller.scrollLeft).toBe(0);
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
  });

  it('chưa có dòng nào thì không lỗi', () => {
    const scroller = document.createElement('div');
    const body = document.createElement('div');
    scroller.appendChild(body);
    expect(() => focusLastRow(scroller, body)).not.toThrow();
  });
});
