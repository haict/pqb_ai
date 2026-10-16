import type { SelectProps } from 'antd';
import { partnerApi, productApi } from '../api';
import { useCompanyId } from '../lookups';
import type { Partner, PartnerRole, Product } from '../types';
import { RemoteSelect } from './RemoteSelect';

type Props = Omit<SelectProps<number>, 'options' | 'showSearch' | 'filterOption' | 'onSearch'> & {
  selectedLabel?: string;
};

/** Chọn hàng hóa đang kinh doanh (tìm theo mã, tên, model). */
export function ProductSelect(props: Props & { onProduct?: (product: Product | undefined) => void }) {
  const companyId = useCompanyId();
  const { onProduct, ...rest } = props;
  return (
    <RemoteSelect<Product>
      placeholder="Tìm hàng theo mã, tên"
      {...rest}
      queryKey="products"
      fetch={async (keyword) => {
        const page = await productApi.list({ page: 0, size: 30, companyId, keyword, status: 'ACTIVE' });
        return page;
      }}
      toOption={(product) => ({ value: product.id, label: `${product.code} - ${product.name}` })}
      onChange={(value, option) => {
        rest.onChange?.(value, option);
        if (onProduct) {
          if (value === undefined || value === null) {
            onProduct(undefined);
          } else {
            productApi.get(value).then(onProduct, () => onProduct(undefined));
          }
        }
      }}
    />
  );
}

/** Chọn đối tác đang hoạt động theo vai trò (chỉ đối tượng có vai trò phù hợp, P-07). */
export function PartnerSelect(props: Props & { role?: PartnerRole }) {
  const companyId = useCompanyId();
  const { role, ...rest } = props;
  return (
    <RemoteSelect<Partner>
      placeholder="Tìm đối tác theo mã, tên, MST"
      {...rest}
      queryKey={`partners-${role ?? 'all'}`}
      fetch={(keyword) => partnerApi.list({ page: 0, size: 30, companyId, keyword, role, status: 'ACTIVE' })}
      toOption={(partner) => ({ value: partner.id, label: `${partner.code} - ${partner.name}` })}
    />
  );
}
