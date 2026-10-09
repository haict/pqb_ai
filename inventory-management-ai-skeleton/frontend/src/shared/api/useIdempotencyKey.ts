import { useCallback, useState } from 'react';
import { newIdempotencyKey } from './httpClient';

/**
 * Một key cho một thao tác người dùng (BR-GEN-50): giữ nguyên khi người dùng bấm gửi lại sau lỗi mạng,
 * đổi key mới khi mở thao tác mới hoặc thao tác trước đã thành công.
 */
export function useIdempotencyKey() {
  const [key, setKey] = useState(newIdempotencyKey);
  const renew = useCallback(() => setKey(newIdempotencyKey()), []);
  return { key, renew };
}
