import { useQuery } from '@tanstack/react-query';
import { Spin } from 'antd';
import { useMemo, type ReactNode } from 'react';
import { getOne } from '../shared/api/rest';
import { PostingModeContext, withPostingMode, type PostingMode } from '../shared/ui/postingMode';
import { ALL_ENABLED, toUiFeatures, UiFeaturesContext, type UiFeatureSetting } from '../shared/ui/uiFeatures';

/**
 * Nạp cấu hình ẩn / đổi tên menu, nút (D-72) và chế độ hạch toán (D-73) sau khi đăng nhập; chờ nạp xong mới vẽ để mục
 * bị ẩn không chớp hiện. Lỗi khi nạp: bật hết, chế độ FULL (đúng mặc định), vì ẩn chỉ phục vụ giao diện, server vẫn
 * kiểm tra quyền và quyết định ghi công nợ theo chế độ của nó.
 */
export function UiFeaturesProvider({ children }: { children: ReactNode }) {
  const settings = useQuery({
    queryKey: ['ui-features'],
    queryFn: () => getOne<UiFeatureSetting[]>('/ui-features'),
    staleTime: Infinity,
    retry: 1,
  });
  const postingMode = useQuery({
    queryKey: ['posting-mode'],
    queryFn: () => getOne<{ mode: PostingMode }>('/posting-mode'),
    staleTime: Infinity,
    retry: 1,
  });
  const mode: PostingMode = postingMode.data?.mode ?? 'FULL';
  const features = useMemo(
    () => withPostingMode(settings.data ? toUiFeatures(settings.data) : ALL_ENABLED, mode),
    [settings.data, mode],
  );

  if (settings.isPending || postingMode.isPending) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" tip="Đang tải cấu hình giao diện...">
          <div style={{ width: 200 }} />
        </Spin>
      </div>
    );
  }
  return (
    <PostingModeContext.Provider value={mode}>
      <UiFeaturesContext.Provider value={features}>{children}</UiFeaturesContext.Provider>
    </PostingModeContext.Provider>
  );
}
