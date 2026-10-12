import { useQuery } from '@tanstack/react-query';
import { productApi } from '../../masterdata/api';
import { useCatalog } from '../../masterdata/lookups';

/** Hàng đã chọn và đơn vị tính dùng được (đơn vị cơ sở + đơn vị quy đổi đang hoạt động). */
export function useProductUnits(productId: number | undefined) {
  const units = useCatalog('units');
  const product = useQuery({
    queryKey: ['products', productId],
    queryFn: () => productApi.get(productId as number),
    enabled: typeof productId === 'number',
  }).data;
  const options = product
    ? [
        product.baseUnitId,
        ...product.units.filter((unit) => unit.status === 'ACTIVE').map((unit) => unit.unitId),
      ]
        .filter((id, index, all) => all.indexOf(id) === index)
        .map((id) => ({ value: id, label: units.nameOf(id) || `#${id}` }))
    : [];
  return { product, options, unitName: units.nameOf };
}
