import type { Dayjs } from 'dayjs';
import type { PartnerRole } from '../../masterdata/types';
import { OPENING_SUB_TYPE } from '../labels';
import type { InventoryDocumentInput, InventoryLineInput } from '../types';

const RECALL_IN_SUB_TYPE = 'RECALL_IN';

/** Giá trị form: ngày dạng Dayjs, đổi sang chuỗi khi gửi. */
export interface DocumentFormValues extends Omit<
  InventoryDocumentInput,
  'documentDate' | 'postingDate' | 'lines'
> {
  documentDate: Dayjs;
  postingDate?: Dayjs;
  /** Chỉ dùng trên form để lọc kho của dòng (D-77); server lấy chi nhánh theo kho. */
  branchId?: number;
  lines: (Omit<InventoryLineInput, 'expiryDate' | 'manufactureDate'> & {
    expiryDate?: Dayjs;
    manufactureDate?: Dayjs;
  })[];
}

export const DOCUMENT_FIELDS = [
  'documentType',
  'subType',
  'documentDate',
  'postingDate',
  'branchId',
  'warehouseId',
  'destinationWarehouseId',
  'partnerId',
  'reasonText',
  'note',
  'issuePolicyId',
  'lines',
];

/** Ô đối tác trên chứng từ kho lập tay: nhãn, vai trò đối tác để lọc, bắt buộc hay không. */
export interface PartnerField {
  label: string;
  role: PartnerRole;
  required: boolean;
}

/**
 * Đối tác theo loại chứng từ: nhập kho = nhà cung cấp, nhập thu hồi = khách trả hàng (bắt buộc), xuất kho / chuyển
 * kho = khách hàng; tồn đầu kỳ và loại khác không có đối tác. Server lưu và in lên phiếu (người giao / nhận hàng).
 */
export function partnerField(
  type: InventoryDocumentInput['documentType'] | undefined,
  subType: string | null | undefined,
): PartnerField | null {
  if (type === 'PNK') {
    if (subType === RECALL_IN_SUB_TYPE) {
      return { label: 'Khách trả hàng', role: 'CUSTOMER', required: true };
    }
    return subType === OPENING_SUB_TYPE ? null : { label: 'Nhà cung cấp', role: 'SUPPLIER', required: false };
  }
  if (type === 'PXK' || type === 'PCK') {
    return { label: 'Khách hàng', role: 'CUSTOMER', required: false };
  }
  return null;
}

/** Phiếu chuyển kho chọn kho xuất / kho nhận ở đầu phiếu; loại khác chọn kho theo dòng (D-77). */
export function hasLineWarehouse(type: InventoryDocumentInput['documentType'] | undefined) {
  return type !== 'PCK';
}

export function toDocumentInput(values: DocumentFormValues): InventoryDocumentInput {
  const lineWarehouse = hasLineWarehouse(values.documentType);
  const header: DocumentFormValues = { ...values };
  delete header.branchId;
  return {
    ...header,
    warehouseId: lineWarehouse ? undefined : values.warehouseId,
    // Đổi loại chứng từ sau khi đã chọn đối tác: bỏ đối tác nếu loại mới không có ô đối tác
    partnerId: partnerField(values.documentType, values.subType) ? values.partnerId : undefined,
    documentDate: values.documentDate.format('YYYY-MM-DD'),
    postingDate: values.postingDate?.format('YYYY-MM-DD'),
    lines: values.lines.map((line) => ({
      ...line,
      expiryDate: line.expiryDate?.format('YYYY-MM-DD'),
      manufactureDate: line.manufactureDate?.format('YYYY-MM-DD'),
      warehouseId: lineWarehouse ? line.warehouseId : undefined,
      // Máy mới / cũ chỉ cho dòng nhập (BR-INV-43): bỏ giá trị còn lại khi đổi loại chứng từ hoặc sang giảm tồn
      itemCondition:
        values.documentType === 'PNK' || (values.documentType === 'PDC' && line.direction === 'IN')
          ? line.itemCondition
          : undefined,
    })),
  };
}
