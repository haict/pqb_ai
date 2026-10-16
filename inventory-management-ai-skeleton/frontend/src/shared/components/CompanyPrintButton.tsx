import { useQuery } from '@tanstack/react-query';
import { Select, Space } from 'antd';
import { useState, type ReactNode } from 'react';
import { PrintButton } from './PrintButton';

/** Công ty chọn để in tên / địa chỉ đầu phiếu (D-58). */
export interface PrintCompany {
  id: number;
  code: string;
  name: string;
  address: string | null;
}

/**
 * Ô chọn công ty in đầu phiếu (mặc định {@code defaultCompanyId}) đặt trước nút in; tên và địa chỉ công ty đang chọn
 * in ở đầu phiếu (D-58). Server kiểm tra công ty trong phạm vi và ghi nhật ký khi in theo công ty khác.
 */
export function CompanyPrintButton({
  label,
  queryKey,
  fetchCompanies,
  defaultCompanyId,
  children,
  extra,
}: {
  label: string;
  queryKey: string;
  fetchCompanies(): Promise<PrintCompany[]>;
  defaultCompanyId: number;
  children(companyId: number): ReactNode;
  /** Nút đặt cạnh nút in, dùng cùng công ty đang chọn (ví dụ tải phiếu về máy). */
  extra?(companyId: number): ReactNode;
}) {
  const [companyId, setCompanyId] = useState<number>(defaultCompanyId);
  const companies = useQuery({
    queryKey: [queryKey, 'print-companies'],
    queryFn: fetchCompanies,
    staleTime: 5 * 60_000,
  });
  return (
    <Space.Compact>
      <Select
        style={{ width: 260 }}
        loading={companies.isPending}
        value={companyId}
        onChange={setCompanyId}
        aria-label="Công ty in trên phiếu"
        options={(companies.data ?? []).map((item) => ({
          value: item.id,
          label: `${item.code} - ${item.name}`,
        }))}
      />
      <PrintButton label={label} title={label}>
        {children(companyId)}
      </PrintButton>
      {extra?.(companyId)}
    </Space.Compact>
  );
}
