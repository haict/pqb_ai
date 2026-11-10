import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, type ReactNode } from 'react';
import { getOne } from '../shared/api/rest';
import { BrandingContext, DEFAULT_BRANDING, toBranding, type BrandingSetting } from '../shared/ui/branding';

/**
 * Nạp tên, logo theo khách hàng (D-72) từ API công khai (trang đăng nhập cũng cần). Lỗi mạng: dùng mặc định, không
 * chặn ứng dụng. Đổi cấu hình trong DB có hiệu lực khi tải lại trang.
 */
export function BrandingProvider({ children }: { children: ReactNode }) {
  const { data } = useQuery({
    queryKey: ['public', 'branding'],
    queryFn: () => getOne<BrandingSetting>('/public/branding'),
    staleTime: Infinity,
    retry: 1,
  });
  const branding = useMemo(() => (data ? toBranding(data) : DEFAULT_BRANDING), [data]);

  useEffect(() => {
    if (data?.appName?.trim()) {
      document.title = branding.appName;
    }
  }, [data, branding.appName]);

  return <BrandingContext.Provider value={branding}>{children}</BrandingContext.Provider>;
}
