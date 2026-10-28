import { describe, expect, it } from 'vitest';
import { questionNote, sendKey } from './assistantChat';
import type { AssistantMessage } from './types';

const message = (partial: Partial<AssistantMessage>): AssistantMessage => ({
  id: 1,
  role: 'USER',
  content: 'Lập đơn mua?',
  status: 'ANSWERED',
  citations: [],
  grounded: null,
  createdAt: '2026-10-28T03:00:00Z',
  ...partial,
});

describe('assistantChat (AI-3)', () => {
  it('gửi lại đúng câu hỏi sau lỗi dùng lại Idempotency-Key; câu khác hoặc hội thoại khác thì key mới (AT-13)', () => {
    const first = sendKey(null, 5, 'Lập đơn mua?');
    expect(sendKey(first, 5, 'Lập đơn mua?')).toBe(first);
    expect(sendKey(first, 5, 'Khóa kỳ?').key).not.toBe(first.key);
    expect(sendKey(first, 6, 'Lập đơn mua?').key).not.toBe(first.key);
  });

  it('ghi chú câu hỏi lỗi / đang chờ; câu đã trả lời và câu trả lời không có ghi chú', () => {
    expect(questionNote(message({ status: 'FAILED' }))).toContain('Gửi lại');
    expect(questionNote(message({ status: 'PENDING' }))).toContain('Đang chờ');
    expect(questionNote(message({}))).toBeNull();
    expect(questionNote(message({ role: 'ASSISTANT', status: 'COMPLETED' }))).toBeNull();
  });
});
