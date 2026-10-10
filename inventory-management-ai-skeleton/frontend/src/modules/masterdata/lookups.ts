import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../shared/auth/authContext';
import {
  ALL_ACTIVE,
  catalogApi,
  categoryApi,
  countryApi,
  referenceItemApi,
  taxRateApi,
  warehouseApi,
  type SimpleCatalogPath,
} from './api';
import type { ReferenceCategory } from './types';

const STALE = 5 * 60_000;

/** Công ty làm việc: P1 vận hành một công ty (D-23), lấy công ty của người dùng. */
export function useCompanyId(): number {
  const { user } = useAuth();
  return user?.companyId ?? 0;
}

function lookup<T extends { id: number; code: string; name: string }>(items: T[]) {
  return {
    items,
    options: items.map((item) => ({ value: item.id, label: `${item.code} - ${item.name}` })),
    nameOf: (id: number | null | undefined) => {
      const item = items.find((candidate) => candidate.id === id);
      return item ? item.name : '';
    },
    codeOf: (id: number | null | undefined) => items.find((candidate) => candidate.id === id)?.code ?? '',
  };
}

export function useCatalog(path: SimpleCatalogPath) {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: [path, 'lookup', companyId],
    queryFn: () => catalogApi.list(path, { ...ALL_ACTIVE, companyId }),
    enabled: companyId > 0,
    staleTime: STALE,
  });
  return lookup(query.data?.items ?? []);
}

export function useCategories() {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['product-categories', 'lookup', companyId],
    queryFn: () => categoryApi.list({ ...ALL_ACTIVE, companyId }),
    enabled: companyId > 0,
    staleTime: STALE,
  });
  return lookup(query.data?.items ?? []);
}

export function useTaxRates() {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['tax-rates', 'lookup', companyId],
    queryFn: () => taxRateApi.list({ ...ALL_ACTIVE, companyId }),
    enabled: companyId > 0,
    staleTime: STALE,
  });
  return lookup(query.data?.items ?? []);
}

/** Kho trong phạm vi dữ liệu (chi nhánh / kho được gán). */
export function useWarehouses() {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['warehouses', 'lookup', companyId],
    queryFn: () => warehouseApi.list({ ...ALL_ACTIVE, companyId }),
    enabled: companyId > 0,
    staleTime: STALE,
  });
  return lookup(query.data?.items ?? []);
}

export function useReferenceItems(category: ReferenceCategory) {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['reference-items', 'lookup', companyId, category],
    queryFn: () => referenceItemApi.list({ ...ALL_ACTIVE, companyId, category }),
    enabled: companyId > 0,
    staleTime: STALE,
  });
  return lookup(query.data?.items ?? []);
}

export function useCountries() {
  const query = useQuery({
    queryKey: ['countries', 'lookup'],
    queryFn: () => countryApi.list({ ...ALL_ACTIVE }),
    staleTime: STALE,
  });
  const items = query.data?.items ?? [];
  return {
    items,
    options: items.map((country) => ({ value: country.code, label: `${country.code} - ${country.name}` })),
  };
}
