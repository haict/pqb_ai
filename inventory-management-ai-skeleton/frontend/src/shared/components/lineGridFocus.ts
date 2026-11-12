const FIRST_FIELD =
  'input:not([disabled]):not([type="hidden"]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** Cuộn ngang về đầu, đưa dòng cuối (dòng vừa thêm) vào tầm nhìn và đặt con trỏ vào ô nhập đầu tiên của dòng. */
export function focusLastRow(scroller: HTMLElement | null, body: HTMLElement | null) {
  const row = body?.lastElementChild;
  if (!scroller || !(row instanceof HTMLElement)) {
    return;
  }
  row.querySelector<HTMLElement>(FIRST_FIELD)?.focus({ preventScroll: true });
  scroller.scrollLeft = 0;
  // 'nearest' + scroll-margin-bottom của dòng: dòng mới nằm ngay trên thanh "Thêm dòng", không bị che.
  row.scrollIntoView?.({ block: 'nearest' });
}
