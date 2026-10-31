import type { Dayjs } from 'dayjs';
import type { Product, ProductType } from '../types';

export type ProductFormValues = Omit<Partial<Product>, 'registrationValidTo'> & {
  registrationValidTo?: Dayjs;
};

export const PRODUCT_FIELDS = [
  'code',
  'name',
  'tradeName',
  'categoryId',
  'productType',
  'baseUnitId',
  'brandId',
  'manufacturerId',
  'originCountryCode',
  'model',
  'specification',
  'description',
  'lotTracked',
  'expiryTracked',
  'serialTracked',
  'medicalDeviceClass',
  'registrationNo',
  'registrationHolder',
  'registrationValidTo',
  'storageMinTemp',
  'storageMaxTemp',
  'coldChain',
  'hazardous',
  'warrantyMonths',
  'maintenanceCycleMonths',
  'requiresInstallation',
  'requiresCalibration',
  'slowMovingDays',
  'defaultTaxRateId',
];

export /** Cờ quản lý mặc định theo loại hàng (BR-MD-04). */
function trackingDefaults(type: ProductType) {
  if (type === 'MACHINE') {
    return { lotTracked: false, expiryTracked: false, serialTracked: true };
  }
  if (type === 'REAGENT' || type === 'CONSUMABLE') {
    return { lotTracked: true, expiryTracked: true, serialTracked: false };
  }
  return { lotTracked: false, expiryTracked: false, serialTracked: false };
}

/** Giá trị mặc định khi thêm hàng mới. */
export const NEW_PRODUCT_VALUES: ProductFormValues = {
  productType: 'CONSUMABLE',
  ...trackingDefaults('CONSUMABLE'),
};

/** Dữ liệu gửi API từ giá trị form (ngày theo YYYY-MM-DD, cờ luôn là boolean). */
export function productPayload(values: ProductFormValues) {
  return {
    ...values,
    registrationValidTo: values.registrationValidTo ? values.registrationValidTo.format('YYYY-MM-DD') : null,
    lotTracked: Boolean(values.lotTracked),
    expiryTracked: Boolean(values.expiryTracked),
    serialTracked: Boolean(values.serialTracked),
    coldChain: Boolean(values.coldChain),
    hazardous: Boolean(values.hazardous),
    requiresInstallation: Boolean(values.requiresInstallation),
    requiresCalibration: Boolean(values.requiresCalibration),
  };
}
