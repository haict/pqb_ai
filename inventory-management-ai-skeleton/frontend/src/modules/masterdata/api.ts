import {
  deleteJson,
  downloadFile,
  getOne,
  getPage,
  patchStatus,
  postJson,
  postWithKey,
  putJson,
  uploadFile,
} from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type { RecordStatus } from '../../shared/components/StatusTag';
import type {
  CatalogItem,
  ComplianceDocument,
  ComplianceDocumentType,
  Country,
  ImportResult,
  Partner,
  TaxCodeLookup,
  PriceList,
  PriceListItem,
  Product,
  ProductCategory,
  ReferenceItem,
  TaxRate,
  Warehouse,
  WarehouseLocation,
} from './types';

/** Danh mục mã - tên dùng chung API: ĐVT, nhãn hiệu, NSX, nhóm đối tác. */
export type SimpleCatalogPath = 'units' | 'brands' | 'manufacturers' | 'partner-groups';

export const catalogApi = {
  list: <F extends object>(path: SimpleCatalogPath, params: ListParams & F) =>
    getPage<CatalogItem>(`/${path}`, params),
  create: (
    path: SimpleCatalogPath,
    input: { companyId: number; code: string; name: string; countryCode?: string },
    key: string,
  ) => postWithKey<CatalogItem>(`/${path}`, input, key),
  update: (
    path: SimpleCatalogPath,
    id: number,
    input: { name: string; countryCode?: string; version: number },
  ) => putJson<CatalogItem>(`/${path}/${id}`, input),
  changeStatus: (path: SimpleCatalogPath, item: CatalogItem, status: RecordStatus, reason: string) =>
    patchStatus<CatalogItem>(`/${path}/${item.id}`, status, item.version, reason),
};

export const countryApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Country>('/countries', params),
  create: (input: { code: string; name: string }, key: string) =>
    postWithKey<Country>('/countries', input, key),
  update: (code: string, input: { name: string; version: number }) =>
    putJson<Country>(`/countries/${code}`, input),
  changeStatus: (item: Country, status: RecordStatus, reason: string) =>
    patchStatus<Country>(`/countries/${item.code}`, status, item.version, reason),
};

export const referenceItemApi = {
  list: <F extends object>(params: ListParams & F) => getPage<ReferenceItem>('/reference-items', params),
  create: (input: object, key: string) => postWithKey<ReferenceItem>('/reference-items', input, key),
  update: (id: number, input: object) => putJson<ReferenceItem>(`/reference-items/${id}`, input),
  changeStatus: (item: ReferenceItem, status: RecordStatus, reason: string) =>
    patchStatus<ReferenceItem>(`/reference-items/${item.id}`, status, item.version, reason),
};

export const taxRateApi = {
  list: <F extends object>(params: ListParams & F) => getPage<TaxRate>('/tax-rates', params),
  create: (input: object, key: string) => postWithKey<TaxRate>('/tax-rates', input, key),
  update: (id: number, input: object) => putJson<TaxRate>(`/tax-rates/${id}`, input),
  changeStatus: (item: TaxRate, status: RecordStatus, reason: string) =>
    patchStatus<TaxRate>(`/tax-rates/${item.id}`, status, item.version, reason),
};

export const categoryApi = {
  list: <F extends object>(params: ListParams & F) => getPage<ProductCategory>('/product-categories', params),
  create: (input: object, key: string) => postWithKey<ProductCategory>('/product-categories', input, key),
  update: (id: number, input: object) => putJson<ProductCategory>(`/product-categories/${id}`, input),
  changeStatus: (item: ProductCategory, status: RecordStatus, reason: string) =>
    patchStatus<ProductCategory>(`/product-categories/${item.id}`, status, item.version, reason),
};

export const productApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Product>('/products', params),
  get: (id: number) => getOne<Product>(`/products/${id}`),
  create: (input: object, key: string) => postWithKey<Product>('/products', input, key),
  update: (id: number, input: object) => putJson<Product>(`/products/${id}`, input),
  changeStatus: (item: Product, status: RecordStatus, reason: string) =>
    patchStatus<Product>(`/products/${item.id}`, status, item.version, reason),
  addUnit: (id: number, input: { unitId: number; conversionFactor: number; effectiveFrom: string }) =>
    postJson<Product>(`/products/${id}/units`, input),
  replaceStockLevels: (id: number, levels: object[]) =>
    putJson<Product>(`/products/${id}/stock-levels`, { levels }),
  downloadTemplate: () => downloadFile('/products/import-template', 'mau-nhap-hang-hoa.xlsx'),
  import: (companyId: number, file: File, key: string) =>
    uploadFile<ImportResult>('/products/import', file, { companyId }, key),
};

export const warehouseApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Warehouse>('/warehouses', params),
  create: (input: object, key: string) => postWithKey<Warehouse>('/warehouses', input, key),
  update: (id: number, input: object) => putJson<Warehouse>(`/warehouses/${id}`, input),
  changeStatus: (item: Warehouse, status: RecordStatus, reason: string) =>
    patchStatus<Warehouse>(`/warehouses/${item.id}`, status, item.version, reason),
  locations: (id: number) => getOne<WarehouseLocation[]>(`/warehouses/${id}/locations`),
  addLocation: (id: number, input: object, key: string) =>
    postWithKey<WarehouseLocation>(`/warehouses/${id}/locations`, input, key),
  updateLocation: (id: number, input: object) =>
    putJson<WarehouseLocation>(`/warehouse-locations/${id}`, input),
  changeLocationStatus: (item: WarehouseLocation, status: RecordStatus, reason: string) =>
    patchStatus<WarehouseLocation>(`/warehouse-locations/${item.id}`, status, item.version, reason),
};

export const partnerApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Partner>('/partners', params),
  get: (id: number) => getOne<Partner>(`/partners/${id}`),
  lookupTaxCode: (taxCode: string) => getOne<TaxCodeLookup>('/partners/tax-code-lookup', { taxCode }),
  create: (input: object, key: string) => postWithKey<Partner>('/partners', input, key),
  update: (id: number, input: object) => putJson<Partner>(`/partners/${id}`, input),
  changeStatus: (item: Partner, status: RecordStatus, reason: string) =>
    patchStatus<Partner>(`/partners/${item.id}`, status, item.version, reason),
  downloadTemplate: () => downloadFile('/partners/import-template', 'mau-nhap-doi-tac.xlsx'),
  import: (companyId: number, file: File, key: string) =>
    uploadFile<ImportResult>('/partners/import', file, { companyId }, key),
};

export const priceListApi = {
  list: <F extends object>(params: ListParams & F) => getPage<PriceList>('/price-lists', params),
  create: (input: object, key: string) => postWithKey<PriceList>('/price-lists', input, key),
  update: (id: number, input: object) => putJson<PriceList>(`/price-lists/${id}`, input),
  changeStatus: (item: PriceList, status: RecordStatus, reason: string) =>
    patchStatus<PriceList>(`/price-lists/${item.id}`, status, item.version, reason),
  items: (id: number, params: ListParams) => getPage<PriceListItem>(`/price-lists/${id}/items`, params),
  upsertItem: (id: number, input: object) => putJson<PriceListItem>(`/price-lists/${id}/items`, input),
  deleteItem: (id: number, itemId: number) => deleteJson(`/price-lists/${id}/items/${itemId}`),
};

export const complianceApi = {
  types: <F extends object>(params: ListParams & F) =>
    getPage<ComplianceDocumentType>('/compliance-document-types', params),
  createType: (input: object, key: string) =>
    postWithKey<ComplianceDocumentType>('/compliance-document-types', input, key),
  updateType: (id: number, input: object) =>
    putJson<ComplianceDocumentType>(`/compliance-document-types/${id}`, input),
  changeTypeStatus: (item: ComplianceDocumentType, status: RecordStatus, reason: string) =>
    patchStatus<ComplianceDocumentType>(
      `/compliance-document-types/${item.id}`,
      status,
      item.version,
      reason,
    ),
  documents: <F extends object>(params: ListParams & F) =>
    getPage<ComplianceDocument>('/compliance-documents', params),
  createDocument: (input: object, key: string) =>
    postWithKey<ComplianceDocument>('/compliance-documents', input, key),
  updateDocument: (id: number, input: object) =>
    putJson<ComplianceDocument>(`/compliance-documents/${id}`, input),
  changeDocumentStatus: (item: ComplianceDocument, status: RecordStatus, reason: string) =>
    patchStatus<ComplianceDocument>(`/compliance-documents/${item.id}`, status, item.version, reason),
};

/** Danh mục đầy đủ (tối đa 200) cho ô chọn. */
export const ALL_ACTIVE = { page: 0, size: 200, status: 'ACTIVE' };
