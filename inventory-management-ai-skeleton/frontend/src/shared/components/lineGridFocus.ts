const FIRST_FIELD =
  'input:not([disabled]):not([type="hidden"]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Cuộn ngang về đầu, đưa dòng vừa thêm vào tầm nhìn và đặt con trỏ vào ô nhập đầu tiên của dòng.
 * {@code index}: vị trí dòng vừa chèn (0 = dòng đầu); không có thì là dòng cuối. Phần tử con đầu tiên của
 * {@code body} là hàng tiêu đề cột nên dòng thứ {@code index} là phần tử con thứ {@code index + 1}.
 */
export function focusRow(scroller: HTMLElement | null, body: HTMLElement | null, index?: number) {
  const row =
    index === undefined ? body?.lastElementChild : (body?.children[index + 1] ?? body?.lastElementChild);
  if (!scroller || !(row instanceof HTMLElement)) {
    return;
  }
  row.querySelector<HTMLElement>(FIRST_FIELD)?.focus({ preventScroll: true });
  scroller.scrollLeft = 0;
  // 'nearest' + scroll-margin-bottom của dòng: dòng mới nằm ngay trên thanh "Thêm dòng", không bị che.
  row.scrollIntoView?.({ block: 'nearest' });
}
