/** Kiểu dữ liệu nhận dạng chứng từ (D-64, docs/14 mục 4.4, 6.1); khớp DTO module ai của backend. */

export type OcrDocumentType = 'PURCHASE_INVOICE' | 'PURCHASE_ORDER' | 'SALES_ORDER';

/** Giá trị đọc từ chứng từ; số tiền, số lượng là chuỗi thập phân. */
export interface OcrValue {
  value: string | null;
  rawText: string | null;
  source: 'OCR' | 'UNKNOWN';
  confidence: number | null;
  status: 'OK' | 'LOW_CONFIDENCE' | 'UNKNOWN';
  warnings: string[];
}

export interface CatalogCandidate {
  id: number;
  code: string;
  name: string;
}

/** Đối chiếu danh mục: chỉ MATCHED mới có id; AMBIGUOUS trả ứng viên, không tự chọn (BR-AI-06). */
export interface CatalogMatch {
  status: 'MATCHED' | 'AMBIGUOUS' | 'NOT_FOUND' | 'UNKNOWN';
  matchedBy: 'CODE' | 'TAX_CODE' | 'NAME' | 'UNIT' | 'RATE' | null;
  reviewRequired: boolean;
  id: number | null;
  code: string | null;
  name: string | null;
  rawText: string | null;
  candidates: CatalogCandidate[];
}

export interface AmountCheck {
  status: 'OK' | 'MISMATCH' | 'UNKNOWN';
  calculated: string | null;
  printed: string | null;
  warnings: string[];
}

/** Dòng hàng gợi ý, chung cho form đơn mua và đơn bán. */
export interface OcrLine {
  product: CatalogMatch;
  unit: CatalogMatch;
  taxRate: CatalogMatch;
  quantity: OcrValue;
  unitPrice: OcrValue;
  discountPercent: OcrValue;
  printedAmount: OcrValue;
  amountCheck: AmountCheck;
}

export interface OcrTotals {
  goods: OcrValue;
  vat: OcrValue;
  total: OcrValue;
  check: AmountCheck;
}

export interface PurchaseOcrResult {
  documentType: OcrDocumentType;
  pageCount: number;
  header: {
    supplier: CatalogMatch;
    invoiceSeries: OcrValue;
    invoiceNo: OcrValue;
    invoiceDate: OcrValue;
    documentDate: OcrValue;
    description: OcrValue;
    currencyCode: OcrValue;
  };
  lines: OcrLine[];
  totals: OcrTotals;
  warnings: string[];
}

/**
 * Gợi ý form đơn bán từ phiếu đặt hàng của khách (AI-2). Địa chỉ giao có thể mất dấu (OCR) nên chỉ hiển thị để đối
 * chiếu, không tự điền.
 */
export interface SalesOcrResult {
  documentType: 'SALES_ORDER';
  pageCount: number;
  header: {
    customer: CatalogMatch;
    documentDate: OcrValue;
    description: OcrValue;
    deliveryAddress: OcrValue;
    currencyCode: OcrValue;
  };
  lines: OcrLine[];
  totals: OcrTotals;
  warnings: string[];
}

export interface AiHealth {
  ocrAvailable: boolean;
  chatAvailable: boolean;
}

/** Hội thoại với trợ lý của chính người dùng (AI-3). */
export interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  lastMessageAt: string;
}

export interface AssistantMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  status: 'PENDING' | 'ANSWERED' | 'FAILED' | 'COMPLETED';
  /** Tên mục tài liệu hướng dẫn được trích (chỉ câu trả lời). */
  citations: string[];
  grounded: boolean | null;
  createdAt: string;
}

export interface AssistantExchange {
  question: AssistantMessage;
  answer: AssistantMessage;
}
