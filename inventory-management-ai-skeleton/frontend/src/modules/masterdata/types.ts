import type { RecordStatus } from '../../shared/components/StatusTag';

/** DTO khớp backend `masterdata/application/dto` (docs/04 M02). */

export interface CatalogItem {
  id: number;
  companyId: number;
  code: string;
  name: string;
  countryCode: string | null;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export interface Country {
  code: string;
  name: string;
  status: RecordStatus;
  version: number;
}

export type ReferenceCategory =
  | 'PAYMENT_TERM'
  | 'PAYMENT_METHOD'
  | 'DELIVERY_METHOD'
  | 'CONTRACT_TYPE'
  | 'COST_ITEM'
  | 'ADJUSTMENT_REASON'
  | 'SCRAP_REASON'
  | 'RETURN_REASON'
  | 'DISCOUNT_REASON'
  | 'REJECT_REASON'
  | 'LOT_OVERRIDE_REASON'
  | 'QUALITY_CHANGE_REASON';

export interface ReferenceItem {
  id: number;
  companyId: number;
  category: ReferenceCategory;
  code: string;
  name: string;
  numericValue: number | null;
  status: RecordStatus;
  version: number;
}

export interface TaxRate {
  id: number;
  companyId: number;
  code: string;
  name: string;
  rate: number;
  validFrom: string;
  validTo: string | null;
  status: RecordStatus;
  version: number;
}

export interface ProductCategory {
  id: number;
  companyId: number;
  parentId: number | null;
  code: string;
  name: string;
  status: RecordStatus;
  version: number;
}

export type ProductType = 'MACHINE' | 'CONSUMABLE' | 'REAGENT' | 'SPARE_PART' | 'TOOL' | 'SERVICE' | 'OTHER';
export type QualityStatus =
  'PENDING_INSPECTION' | 'PASSED' | 'QUARANTINE' | 'FAILED' | 'EXPIRED' | 'RECALLED';

export interface ProductUnit {
  id: number;
  unitId: number;
  conversionFactor: number;
  effectiveFrom: string;
  status: RecordStatus;
  version: number;
}

export interface StockLevel {
  warehouseId: number;
  minQuantity: number | null;
  maxQuantity: number | null;
}

export interface Product {
  id: number;
  companyId: number;
  code: string;
  legacyCode: string | null;
  name: string;
  tradeName: string | null;
  categoryId: number;
  productType: ProductType;
  baseUnitId: number;
  brandId: number | null;
  manufacturerId: number | null;
  originCountryCode: string | null;
  model: string | null;
  specification: string | null;
  description: string | null;
  lotTracked: boolean;
  expiryTracked: boolean;
  serialTracked: boolean;
  medicalDeviceClass: string | null;
  registrationNo: string | null;
  registrationHolder: string | null;
  registrationValidTo: string | null;
  storageMinTemp: number | null;
  storageMaxTemp: number | null;
  coldChain: boolean;
  hazardous: boolean;
  warrantyMonths: number | null;
  maintenanceCycleMonths: number | null;
  requiresInstallation: boolean;
  requiresCalibration: boolean;
  slowMovingDays: number | null;
  defaultTaxRateId: number | null;
  status: RecordStatus;
  structureLocked: boolean;
  units: ProductUnit[];
  stockLevels: StockLevel[];
  version: number;
}

export type WarehouseType = 'NORMAL' | 'VIRTUAL';
export type VirtualWarehouseKind = 'IN_TRANSIT' | 'AT_CUSTOMER' | 'IN_REPAIR' | 'DEMO';
export type LocationZone = 'NORMAL' | 'QUARANTINE' | 'DAMAGED';

export interface Warehouse {
  id: number;
  companyId: number;
  branchId: number;
  code: string;
  name: string;
  warehouseType: WarehouseType;
  virtualKind: VirtualWarehouseKind | null;
  address: string | null;
  storageCondition: string | null;
  status: RecordStatus;
  version: number;
}

export interface WarehouseLocation {
  id: number;
  warehouseId: number;
  code: string;
  name: string;
  zoneType: LocationZone;
  storageCondition: string | null;
  defaultLocation: boolean;
  status: RecordStatus;
  version: number;
}

export type PartnerRole = 'CUSTOMER' | 'SUPPLIER' | 'AGENT' | 'TENDER_OWNER' | 'EMPLOYEE' | 'OTHER';
export type PartnerKind = 'ORGANIZATION' | 'INDIVIDUAL';
export type CustomerType =
  | 'PUBLIC_HOSPITAL'
  | 'MEDICAL_CENTER'
  | 'PRIVATE_CLINIC'
  | 'PHARMACY'
  | 'ENTERPRISE'
  | 'AGENT'
  | 'INDIVIDUAL';

export interface PartnerContact {
  name: string;
  position?: string | null;
  phone?: string | null;
  email?: string | null;
  primaryContact: boolean;
}

export interface PartnerAddress {
  address: string;
  receiverName?: string | null;
  phone?: string | null;
  defaultAddress: boolean;
}

export interface Partner {
  id: number;
  companyId: number;
  code: string;
  legacyCode: string | null;
  name: string;
  shortName: string | null;
  partnerKind: PartnerKind;
  taxCode: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  countryCode: string | null;
  partnerGroupId: number | null;
  roles: PartnerRole[];
  customerType: CustomerType | null;
  salesUserId: number | null;
  managingBranchId: number | null;
  paymentTermDays: number | null;
  creditLimit: number | null;
  minRemainingShelfLifeDays: number | null;
  priceListId: number | null;
  supplierPaymentTermDays: number | null;
  currencyCode: string | null;
  distributedBrands: string | null;
  agentDiscountPercent: number | null;
  agentRegion: string | null;
  agentCreditLimit: number | null;
  contacts: PartnerContact[];
  addresses: PartnerAddress[];
  status: RecordStatus;
  version: number;
}

export type PriceType = 'PURCHASE_REFERENCE' | 'LIST' | 'FLOOR' | 'SALES';
export type PriceTargetType = 'ALL' | 'PARTNER_GROUP' | 'PARTNER';

export interface PriceList {
  id: number;
  companyId: number;
  code: string;
  name: string;
  priceType: PriceType;
  targetType: PriceTargetType;
  partnerGroupId: number | null;
  partnerId: number | null;
  currencyCode: string;
  validFrom: string;
  validTo: string | null;
  status: RecordStatus;
  version: number;
}

export interface PriceListItem {
  id: number;
  priceListId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  minQuantity: number;
  unitPrice: number;
}

export type ComplianceOwnerType = 'PRODUCT' | 'LOT' | 'SERIAL' | 'PARTNER' | 'COMPANY';

export interface ComplianceDocumentType {
  id: number;
  companyId: number;
  code: string;
  name: string;
  appliesTo: ComplianceOwnerType;
  hasExpiry: boolean;
  requiredProductTypes: ProductType[];
  status: RecordStatus;
  version: number;
}

export interface ComplianceDocument {
  id: number;
  companyId: number;
  documentTypeId: number;
  ownerType: ComplianceOwnerType;
  ownerId: number;
  documentNo: string;
  issuedDate: string | null;
  expiryDate: string | null;
  daysToExpiry: number | null;
  issuer: string | null;
  note: string | null;
  status: RecordStatus;
  version: number;
}

export interface ImportResult {
  created: number;
}
