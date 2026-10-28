import { useQuery } from '@tanstack/react-query';
import { httpClient } from '../../shared/api/httpClient';
import { deleteJson, getOne, getPage, postWithKey, withoutEmpty } from '../../shared/api/rest';
import type { ApiResponse } from '../../shared/api/types';
import { IDEMPOTENCY_HEADER } from '../../shared/api/httpClient';
import type { AiHealth, AssistantExchange, AssistantMessage, Conversation, OcrDocumentType } from './types';

const OCR_TIMEOUT_MS = 90_000;
/** Server tự dừng ở ai.chat.timeout (90 s) và trả 504; chờ thêm để nhận đúng mã lỗi. */
const CHAT_TIMEOUT_MS = 120_000;
const CONVERSATIONS = '/assistant/conversations';
/** Bằng kích thước trang tối đa của API (api.md mục 4). */
const MAX_PAGE_SIZE = 200;

export interface OcrParams {
  companyId: number;
  branchId: number;
  documentType: OcrDocumentType;
  documentDate?: string;
}

export const aiApi = {
  health: () => getOne<AiHealth>('/ai/health'),
  /** Chỉ trả gợi ý điền form, không tạo dữ liệu: server không cần Idempotency-Key (Q-06). */
  /** Kiểu kết quả theo documentType: PurchaseOcrResult (đơn mua) hoặc SalesOcrResult (đơn bán). */
  recognize: async <R>(file: File, params: OcrParams): Promise<R> => {
    const form = new FormData();
    form.append('file', file);
    const response = await httpClient.post<ApiResponse<R>>('/ocr/recognitions', form, {
      params: withoutEmpty(params),
      // Ghi đè Content-Type JSON mặc định của client (giống uploadFile).
      headers: { 'Content-Type': 'multipart/form-data' },
      // Nhận dạng có thể lâu hơn thời gian chờ chung 30 s; server tự dừng ở ai.ocr.timeout (60 s) và trả 504.
      timeout: OCR_TIMEOUT_MS,
    });
    return response.data.data;
  },
};

/** Trợ lý hỏi đáp cách dùng ERP (AI-3): hội thoại của chính người dùng. */
export const assistantApi = {
  conversations: () => getPage<Conversation>(CONVERSATIONS, { page: 0, size: 50 }),
  create: (idempotencyKey: string) => postWithKey<Conversation>(CONVERSATIONS, {}, idempotencyKey),
  messages: (id: number) =>
    getPage<AssistantMessage>(`${CONVERSATIONS}/${id}/messages`, { page: 0, size: MAX_PAGE_SIZE }),
  /** Gửi lại cùng key sau lỗi mạng không tạo câu hỏi trùng, không gọi AI lại khi đã có trả lời. */
  send: async (id: number, content: string, idempotencyKey: string): Promise<AssistantExchange> => {
    const response = await httpClient.post<ApiResponse<AssistantExchange>>(
      `${CONVERSATIONS}/${id}/messages`,
      { content },
      { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }, timeout: CHAT_TIMEOUT_MS },
    );
    return response.data.data;
  },
  remove: (id: number) => deleteJson(`${CONVERSATIONS}/${id}`),
};

/** Trạng thái AI để ẩn nút khi AI Service ngừng (BR-AI-09); lỗi coi như không khả dụng. */
export function useAiHealth(enabled: boolean): AiHealth {
  const query = useQuery({
    queryKey: ['ai-health'],
    queryFn: aiApi.health,
    enabled,
    staleTime: 30_000,
    retry: false,
  });
  return query.data ?? { ocrAvailable: false, chatAvailable: false };
}
