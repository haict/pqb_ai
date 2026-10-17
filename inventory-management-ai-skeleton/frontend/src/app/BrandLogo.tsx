import { MedicineBoxOutlined } from '@ant-design/icons';
import { BRAND } from './theme';

/** Nhận diện ứng dụng: biểu tượng hộp y tế + tên; {@code compact} chỉ hiện biểu tượng (menu thu gọn). */
export function BrandLogo({ compact = false, inverse = false }: { compact?: boolean; inverse?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden', whiteSpace: 'nowrap' }}>
      <span
        style={{
          flex: 'none',
          width: 36,
          height: 36,
          borderRadius: 10,
          display: 'grid',
          placeItems: 'center',
          fontSize: 19,
          color: inverse ? BRAND.primary : '#fff',
          background: inverse ? '#fff' : BRAND.gradient,
          boxShadow: inverse ? undefined : '0 4px 10px rgba(13, 148, 136, 0.3)',
        }}
      >
        <MedicineBoxOutlined />
      </span>
      {!compact && (
        <span style={{ lineHeight: 1.2 }}>
          <span
            style={{ display: 'block', fontWeight: 700, fontSize: 15, color: inverse ? '#fff' : BRAND.text }}
          >
            ERP Y tế
          </span>
          <span
            style={{
              display: 'block',
              fontSize: 12,
              color: inverse ? 'rgba(255,255,255,0.85)' : BRAND.muted,
            }}
          >
            Thiết bị - Vật tư y tế
          </span>
        </span>
      )}
    </div>
  );
}
