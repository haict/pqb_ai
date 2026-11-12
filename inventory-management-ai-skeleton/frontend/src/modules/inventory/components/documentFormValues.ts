import type { Dayjs } from 'dayjs';
import type { InventoryDocumentInput, InventoryLineInput } from '../types';

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
