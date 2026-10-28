import { newIdempotencyKey } from '../../shared/api/httpClient';
import type { AssistantMessage } from './types';

/** Lần gửi chưa thành công: gửi lại đúng câu hỏi này thì dùng lại key (AT-13), câu khác thì key mới. */
export interface PendingSend {
  conversationId: number;
  content: string;
  key: string;
}

export function sendKey(previous: PendingSend | null, conversationId: number, content: string): PendingSend {
  if (previous && previous.conversationId === conversationId && previous.content === content) {
    return previous;
  }
  return { conversationId, content, key: newIdempotencyKey() };
}

/** Ghi chú dưới câu hỏi chưa có trả lời (lỗi hoặc đang xử lý ở lần trước). */
export function questionNote(message: AssistantMessage): string | null {
  if (message.role !== 'USER') {
    return null;
  }
  switch (message.status) {
    case 'FAILED':
      return 'Chưa trả lời được câu hỏi này. Gửi lại để thử lần nữa.';
    case 'PENDING':
      return 'Đang chờ trả lời…';
    default:
      return null;
  }
}
