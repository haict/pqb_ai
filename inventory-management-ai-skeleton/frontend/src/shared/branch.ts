/**
 * Tạm ẩn thông tin chi nhánh trên mọi màn hình: chứng từ ngầm định chi nhánh mặc định của người dùng
 * (hoặc chi nhánh đầu tiên trong phạm vi), báo cáo lấy toàn công ty. Backend không đổi, vẫn kiểm tra phạm vi.
 */
export const BRANCH_VISIBLE = false;

/** Thêm phần tử (cột bảng, lựa chọn...) chỉ khi đang hiển thị chi nhánh. */
export function whenBranchVisible<T>(...items: T[]): T[] {
  return BRANCH_VISIBLE ? items : [];
}
