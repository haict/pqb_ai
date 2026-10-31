import type { Partner, TaxCodeLookup } from '../types';

export const PARTNER_FIELDS = [
  'code',
  'name',
  'shortName',
  'partnerKind',
  'taxCode',
  'address',
  'phone',
  'email',
  'countryCode',
  'partnerGroupId',
  'roles',
  'customerType',
  'salesUserId',
  'managingBranchId',
  'paymentTermDays',
  'creditLimit',
  'minRemainingShelfLifeDays',
  'priceListId',
  'supplierPaymentTermDays',
  'currencyCode',
  'distributedBrands',
  'agentDiscountPercent',
  'agentRegion',
  'agentCreditLimit',
  'contacts',
  'addresses',
];

/** Dữ liệu gửi API từ giá trị form (cờ chính / mặc định luôn là boolean). */
export function partnerPayload(values: Partial<Partner>) {
  return {
    ...values,
    contacts: (values.contacts ?? []).map((contact) => ({
      ...contact,
      primaryContact: Boolean(contact.primaryContact),
    })),
    addresses: (values.addresses ?? []).map((address) => ({
      ...address,
      defaultAddress: Boolean(address.defaultAddress),
    })),
  };
}

type LookupField = 'name' | 'shortName' | 'address' | 'phone';

const LOOKUP_LABELS: Record<LookupField, string> = {
  name: 'Tên',
  shortName: 'Tên viết tắt',
  address: 'Địa chỉ',
  phone: 'Điện thoại',
};

/**
 * Điền form đối tác từ kết quả tra cứu mã số thuế (D-66): chỉ điền ô còn trống; ô đã có giá trị khác thì ghi chú để
 * người dùng tự quyết.
 */
export function taxCodeFill(
  current: Partial<Record<LookupField, string | null | undefined>>,
  result: TaxCodeLookup,
): { values: Partial<Record<LookupField, string>>; notes: string[] } {
  const values: Partial<Record<LookupField, string>> = {};
  const notes: string[] = [];
  (Object.keys(LOOKUP_LABELS) as LookupField[]).forEach((field) => {
    const found = result[field]?.trim();
    if (!found) {
      return;
    }
    const existing = current[field]?.trim();
    if (!existing) {
      values[field] = found;
    } else if (existing !== found) {
      notes.push(`${LOOKUP_LABELS[field]} theo mã số thuế: ${found}`);
    }
  });
  if (result.status && !/đang hoạt động/i.test(result.status)) {
    notes.push(`Tình trạng theo mã số thuế: ${result.status}`);
  }
  return { values, notes };
}
