import type {
  ComplianceOwnerType,
  CustomerType,
  LocationZone,
  PartnerRole,
  PriceTargetType,
  PriceType,
  ProductType,
  QualityStatus,
  ReferenceCategory,
  VirtualWarehouseKind,
} from './types';

/** Nhãn tiếng Việt cho giá trị enum (dùng cho bảng, ô chọn). */
export const PRODUCT_TYPES: Record<ProductType, string> = {
  MACHINE: 'Máy móc - thiết bị',
  CONSUMABLE: 'Vật tư tiêu hao',
  REAGENT: 'Thuốc thử - hóa chất',
  SPARE_PART: 'Linh kiện - phụ tùng',
  TOOL: 'Dụng cụ',
  SERVICE: 'Dịch vụ',
  OTHER: 'Khác',
};

export const QUALITY_STATUSES: Record<QualityStatus, { label: string; color: string }> = {
  PENDING_INSPECTION: { label: 'Chờ kiểm tra', color: 'gold' },
  PASSED: { label: 'Đạt', color: 'green' },
  QUARANTINE: { label: 'Cách ly', color: 'orange' },
  FAILED: { label: 'Không đạt', color: 'red' },
  EXPIRED: { label: 'Hết hạn', color: 'volcano' },
  RECALLED: { label: 'Thu hồi', color: 'magenta' },
};

export const PARTNER_ROLES: Record<PartnerRole, string> = {
  CUSTOMER: 'Khách hàng',
  SUPPLIER: 'Nhà cung cấp',
  AGENT: 'Đại lý',
  TENDER_OWNER: 'Bên mời thầu',
  EMPLOYEE: 'Nhân viên',
  OTHER: 'Khác',
};

export const CUSTOMER_TYPES: Record<CustomerType, string> = {
  PUBLIC_HOSPITAL: 'Bệnh viện công',
  MEDICAL_CENTER: 'Trung tâm y tế',
  PRIVATE_CLINIC: 'Phòng khám - BV tư',
  PHARMACY: 'Nhà thuốc',
  ENTERPRISE: 'Doanh nghiệp',
  AGENT: 'Đại lý',
  INDIVIDUAL: 'Cá nhân',
};

export const VIRTUAL_KINDS: Record<VirtualWarehouseKind, string> = {
  IN_TRANSIT: 'Hàng đang vận chuyển',
  AT_CUSTOMER: 'Hàng gửi tại khách',
  IN_REPAIR: 'Hàng đang sửa chữa',
  DEMO: 'Hàng demo',
};

export const LOCATION_ZONES: Record<LocationZone, string> = {
  NORMAL: 'Bình thường',
  QUARANTINE: 'Cách ly',
  DAMAGED: 'Hỏng - hết hạn',
};

export const PRICE_TYPES: Record<PriceType, string> = {
  PURCHASE_REFERENCE: 'Giá mua tham khảo',
  LIST: 'Giá niêm yết',
  FLOOR: 'Giá sàn',
  SALES: 'Bảng giá bán',
};

export const PRICE_TARGETS: Record<PriceTargetType, string> = {
  ALL: 'Mọi khách hàng',
  PARTNER_GROUP: 'Nhóm đối tác',
  PARTNER: 'Một đối tác',
};

export const REFERENCE_CATEGORIES: Record<ReferenceCategory, string> = {
  PAYMENT_TERM: 'Điều khoản thanh toán (số ngày)',
  PAYMENT_METHOD: 'Phương thức thanh toán',
  DELIVERY_METHOD: 'Phương thức giao hàng',
  CONTRACT_TYPE: 'Loại hợp đồng',
  COST_ITEM: 'Khoản mục chi phí',
  ADJUSTMENT_REASON: 'Lý do điều chỉnh tồn',
  SCRAP_REASON: 'Lý do hủy hàng',
  RETURN_REASON: 'Lý do trả hàng',
  DISCOUNT_REASON: 'Lý do giảm giá',
  REJECT_REASON: 'Lý do từ chối',
  LOT_OVERRIDE_REASON: 'Lý do chọn lô khác FEFO',
  QUALITY_CHANGE_REASON: 'Lý do đổi trạng thái chất lượng',
};

export const COMPLIANCE_OWNERS: Record<ComplianceOwnerType, string> = {
  PRODUCT: 'Hàng hóa',
  LOT: 'Lô hàng',
  SERIAL: 'Serial',
  PARTNER: 'Đối tác',
  COMPANY: 'Công ty',
};

export function toOptions<T extends string>(labels: Record<T, string>) {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }));
}
