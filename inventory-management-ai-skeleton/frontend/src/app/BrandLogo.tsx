import logo from '../assets/pqb-logo.svg';
import { BRAND } from './theme';

/** Nhận diện ứng dụng: logo PQB + tên; {@code compact} chỉ hiện logo (menu thu gọn). */
export function BrandLogo({ compact = false, inverse = false }: { compact?: boolean; inverse?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden' }}>
      <img
        src={logo}
        alt="PQB Medical Device"
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
          Công nghệ y tế &amp; giáo dục PQP
        </span>
      )}
    </div>
  );
}
