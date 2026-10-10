import type { Dayjs } from 'dayjs';
import type { InventoryDocumentInput, InventoryLineInput } from '../types';

/** Giá trị form: ngày dạng Dayjs, đổi sang chuỗi khi gửi. */
export interface DocumentFormValues extends Omit<
  InventoryDocumentInput,
  'documentDate' | 'postingDate' | 'lines'
> {
  documentDate: Dayjs;
  postingDate?: Dayjs;
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
  'warehouseId',
  'destinationWarehouseId',
  'partnerId',
  'reasonText',
  'note',
  'issuePolicyId',
  'lines',
];

export function toDocumentInput(values: DocumentFormValues): InventoryDocumentInput {
  return {
    ...values,
    documentDate: values.documentDate.format('YYYY-MM-DD'),
    postingDate: values.postingDate?.format('YYYY-MM-DD'),
    lines: values.lines.map((line) => ({
      ...line,
      expiryDate: line.expiryDate?.format('YYYY-MM-DD'),
      manufactureDate: line.manufactureDate?.format('YYYY-MM-DD'),
    })),
  };
}
