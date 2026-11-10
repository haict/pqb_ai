import { useEffect } from 'react';

const FOCUSABLE = 'input, select, textarea, button, [tabindex]';

/** Ô có thể nhận focus theo thứ tự Tab trong form: bỏ ô ẩn, vô hiệu, chỉ đọc (trừ ô chọn của Select). */
function isTabStop(element: HTMLElement): boolean {
  if (element.tabIndex < 0 || element.getClientRects().length === 0) {
    return false;
  }
  if (element instanceof HTMLInputElement || element instanceof HTMLButtonElement) {
    if (element.disabled || (element instanceof HTMLInputElement && element.type === 'hidden')) {
      return false;
    }
  }
  if (element instanceof HTMLInputElement && element.readOnly && !element.closest('.ant-select')) {
    return false;
  }
  return true;
}

function nextTabStop(form: HTMLFormElement, current: HTMLElement): HTMLElement | undefined {
  const stops = Array.from(form.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(isTabStop);
  const index = stops.findIndex((element) => element === current || element.contains(current));
  return index >= 0 ? stops[index + 1] : undefined;
}

/**
 * Xác định có chuyển Enter thành Tab hay không. Giữ Enter mặc định cho: ô nhiều dòng, nút, liên kết, form tìm kiếm
 * (có nút submit: Enter = tìm), Select nhiều giá trị đang mở (Enter chọn tiếp), phím kèm Ctrl / Shift / Alt / Meta.
 */
export function enterTarget(event: KeyboardEvent): { form: HTMLFormElement; field: HTMLElement } | null {
  if (
    event.key !== 'Enter' ||
    event.isComposing ||
    event.ctrlKey ||
    event.shiftKey ||
    event.altKey ||
    event.metaKey
  ) {
    return null;
  }
  const field = event.target;
  if (
    !(field instanceof HTMLElement) ||
    field instanceof HTMLTextAreaElement ||
    field instanceof HTMLButtonElement
  ) {
    return null;
  }
  if (
    field instanceof HTMLAnchorElement ||
    field.isContentEditable ||
    field.closest('[data-enter-default]')
  ) {
    return null;
  }
  const form = field.closest('form');
  if (!form || form.querySelector('button[type="submit"]')) {
    return null;
  }
  if (field.closest('.ant-select-multiple') && field.getAttribute('aria-expanded') === 'true') {
    return null;
  }
  return { form, field };
}

function handleKeyDown(event: KeyboardEvent) {
  const target = enterTarget(event);
  if (!target) {
    return;
  }
  // Không submit form; Select / DatePicker / AutoComplete đã xử lý Enter (chọn dòng, chốt ngày gõ tay) trước khi tới
  // document, nên chuyển ô sau khi chúng cập nhật xong.
  event.preventDefault();
  window.setTimeout(() => nextTabStop(target.form, target.field)?.focus(), 0);
}

/** Enter trong form nhập liệu chuyển sang ô kế tiếp như Tab; ô cuối form giữ nguyên. */
export function useEnterAsTab() {
  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);
}
