import { MedicineBoxOutlined } from '@ant-design/icons';
import { BRAND } from './theme';

/** Nhận diện ứng dụng: biểu tượng hộp y tế + tên; {@code compact} chỉ hiện biểu tượng (menu thu gọn). */
export function BrandLogo({ compact = false, inverse = false }: { compact?: boolean; inverse?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden', whiteSpace: 'nowrap' }}>
      <span
        style={{
          flex: 'none',
          width: 34,
          height: 34,
          borderRadius: 8,
          display: 'grid',
          placeItems: 'center',
          fontSize: 18,
          color: inverse ? BRAND.primary : '#fff',
          background: inverse ? '#fff' : `linear-gradient(135deg, ${BRAND.primary}, ${BRAND.primaryDark})`,
        }}
      >
        <MedicineBoxOutlined />
      </span>
      {!compact && (
        <span style={{ lineHeight: 1.2 }}>
          <span style={{ display: 'block', fontWeight: 700, color: inverse ? '#fff' : BRAND.primaryDark }}>
            ERP Y tế
          </span>
          <span
            style={{ display: 'block', fontSize: 12, color: inverse ? 'rgba(255,255,255,0.85)' : '#6b7c85' }}
          >
            Thiết bị - Vật tư y tế
          </span>
        </span>
      )}
    </div>
  );
}
