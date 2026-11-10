import { useBranding } from '../shared/ui/branding';
import { BRAND } from './theme';

/** Nhận diện ứng dụng: logo + tên theo cấu hình khách hàng (D-72); {@code compact} chỉ hiện logo (menu thu gọn). */
export function BrandLogo({ compact = false, inverse = false }: { compact?: boolean; inverse?: boolean }) {
  const branding = useBranding();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden' }}>
      <img
        src={branding.logoUrl}
        alt={branding.appName}
        style={{
          flex: 'none',
          // Menu thu gọn chỉ rộng 64px nên giữ cỡ nhỏ.
          width: compact ? 40 : 60,
          height: compact ? 40 : 60,
          objectFit: 'contain',
          // Khung trắng giữ màu xanh logo rõ trên nền tối của trang đăng nhập.
          padding: 2,
          borderRadius: 8,
          background: '#fff',
        }}
      />
      {!compact && (
        <span
          style={{
            fontSize: 14,
            lineHeight: 1.25,
            color: inverse ? '#fff' : BRAND.text,
          }}
        >
          {branding.appName}
        </span>
      )}
    </div>
  );
}
