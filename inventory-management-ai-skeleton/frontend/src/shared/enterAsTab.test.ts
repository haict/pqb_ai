import { enterTarget } from './enterAsTab';

function keyOn(element: HTMLElement, init: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, ...init });
  Object.defineProperty(event, 'target', { value: element });
  return event;
}

function form(html: string): HTMLFormElement {
  const element = document.createElement('form');
  element.innerHTML = html;
  document.body.replaceChildren(element);
  return element;
}

describe('enterTarget', () => {
  it('Enter trong ô nhập của form được chuyển như Tab', () => {
    const root = form('<input id="a" /><input id="b" />');
    const input = root.querySelector<HTMLInputElement>('#a')!;
    expect(enterTarget(keyOn(input))).toEqual({ form: root, field: input });
  });

  it('giữ Enter mặc định cho ô nhiều dòng, nút, phím kèm Shift / Ctrl', () => {
    const root = form(
      '<input id="a" /><textarea id="t"></textarea><button id="b" type="button">Lưu</button>',
    );
    expect(enterTarget(keyOn(root.querySelector('#t')!))).toBeNull();
    expect(enterTarget(keyOn(root.querySelector('#b')!))).toBeNull();
    expect(enterTarget(keyOn(root.querySelector('#a')!, { shiftKey: true }))).toBeNull();
    expect(enterTarget(keyOn(root.querySelector('#a')!, { ctrlKey: true }))).toBeNull();
  });

  it('form tìm kiếm có nút submit: Enter vẫn là tìm', () => {
    const root = form('<input id="a" /><button type="submit">Xem</button>');
    expect(enterTarget(keyOn(root.querySelector('#a')!))).toBeNull();
  });

  it('ô ngoài form và phím khác Enter không bị đổi', () => {
    const input = document.createElement('input');
    document.body.replaceChildren(input);
    expect(enterTarget(keyOn(input))).toBeNull();
    const root = form('<input id="a" />');
    expect(enterTarget(keyOn(root.querySelector('#a')!, { key: 'Tab' }))).toBeNull();
  });

  it('Select nhiều giá trị đang mở: Enter để chọn tiếp', () => {
    const root = form('<div class="ant-select-multiple"><input id="s" aria-expanded="true" /></div>');
    expect(enterTarget(keyOn(root.querySelector('#s')!))).toBeNull();
  });
});
