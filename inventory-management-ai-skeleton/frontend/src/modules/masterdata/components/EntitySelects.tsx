import { DownOutlined, PlusOutlined } from '@ant-design/icons';
import { App, Tooltip, type SelectProps } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { partnerApi, productApi } from '../api';
import { useCompanyId } from '../lookups';
import type { Partner, PartnerRole, Product } from '../types';
import { PartnerForm } from './PartnerForm';
import { PARTNER_FIELDS, partnerPayload } from './partnerFields';
import { ProductForm } from './ProductForm';
import { NEW_PRODUCT_VALUES, PRODUCT_FIELDS, productPayload, type ProductFormValues } from './productFields';
import { RemoteSelect } from './RemoteSelect';

type Props = Omit<SelectProps<number>, 'options' | 'showSearch' | 'filterOption' | 'onSearch'> & {
  selectedLabel?: string;
};

interface Created {
  id: number;
  label: string;
}

/** Dấu + cạnh mũi tên của ô chọn, mở popup thêm danh mục. */
function CreateSuffix({ title, onClick }: { title: string; onClick(): void }): ReactNode {
  return (
    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
      <Tooltip title={title}>
        <PlusOutlined
          aria-label={title}
          style={{ pointerEvents: 'auto', cursor: 'pointer', color: '#1677ff' }}
          onMouseDown={(event) => {
            // Không để Select mở danh sách khi bấm dấu +
            event.preventDefault();
            event.stopPropagation();
          }}
          onClick={(event) => {
            event.stopPropagation();
            onClick();
          }}
        />
      </Tooltip>
      <DownOutlined />
    </span>
  );
}

/**
 * Chọn hàng hóa đang kinh doanh (tìm theo mã, tên, model). {@code creatable}: dấu + trong ô mở popup thêm hàng hóa
 * (người có quyền product:manage), thêm xong chọn luôn hàng mới.
 */
export function ProductSelect(
  props: Props & { onProduct?: (product: Product | undefined) => void; creatable?: boolean },
) {
  const companyId = useCompanyId();
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { onProduct, creatable, selectedLabel, ...rest } = props;
  const [creating, setCreating] = useState(false);
  // Hàng vừa thêm có thể chưa nằm trong kết quả tìm kiếm đang hiển thị
  const [created, setCreated] = useState<Created>();
  const canCreate = Boolean(creatable) && can('product:manage') && !rest.disabled;

  const change: SelectProps<number>['onChange'] = (value, option) => {
    rest.onChange?.(value, option);
    if (onProduct) {
      if (value === undefined || value === null) {
        onProduct(undefined);
      } else {
        productApi.get(value).then(onProduct, () => onProduct(undefined));
      }
    }
  };

  const save = async (values: ProductFormValues, key: string) => {
    const product = await productApi.create({ ...productPayload(values), companyId }, key);
    const label = `${product.code} - ${product.name}`;
    message.success('Đã thêm hàng hóa.');
    setCreated({ id: product.id, label });
    await queryClient.invalidateQueries({ queryKey: ['products'] });
    change(product.id, { value: product.id, label });
  };

  return (
    <>
      <RemoteSelect<Product>
        placeholder="Tìm hàng theo mã, tên"
        {...rest}
        selectedLabel={created && created.id === rest.value ? created.label : selectedLabel}
        suffixIcon={
          canCreate ? (
            <CreateSuffix title="Thêm hàng hóa" onClick={() => setCreating(true)} />
          ) : (
            rest.suffixIcon
          )
        }
        queryKey="products"
        fetch={(keyword) => productApi.list({ page: 0, size: 30, companyId, keyword, status: 'ACTIVE' })}
        toOption={(product) => ({ value: product.id, label: `${product.code} - ${product.name}` })}
        onChange={change}
      />
      {canCreate && (
        <FormModal<ProductFormValues>
          open={creating}
          width={860}
          title="Thêm hàng hóa"
          initialValues={NEW_PRODUCT_VALUES}
          fieldNames={PRODUCT_FIELDS}
          onSubmit={save}
          onClose={() => setCreating(false)}
        >
          {(modalForm) => <ProductForm form={modalForm} editing={null} />}
        </FormModal>
      )}
    </>
  );
}

/**
 * Chọn đối tác đang hoạt động theo vai trò (chỉ đối tượng có vai trò phù hợp, P-07). {@code creatable}: dấu + trong ô
 * mở popup thêm đối tác (người có quyền partner:manage), thêm xong chọn luôn đối tác mới.
 */
export function PartnerSelect(props: Props & { role?: PartnerRole; creatable?: boolean }) {
  const companyId = useCompanyId();
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { role, creatable, selectedLabel, ...rest } = props;
  const [creating, setCreating] = useState(false);
  // Đối tác vừa thêm có thể chưa nằm trong kết quả tìm kiếm đang hiển thị
  const [created, setCreated] = useState<Created>();
  const canCreate = Boolean(creatable) && can('partner:manage') && !rest.disabled;

  const save = async (values: Partial<Partner>, key: string) => {
    const partner = await partnerApi.create({ ...partnerPayload(values), companyId }, key);
    const label = `${partner.code} - ${partner.name}`;
    message.success('Đã thêm đối tác.');
    setCreated({ id: partner.id, label });
    await queryClient.invalidateQueries({ queryKey: ['partners'] });
    await queryClient.invalidateQueries({ queryKey: [`partners-${role ?? 'all'}`] });
    rest.onChange?.(partner.id, { value: partner.id, label });
  };

  return (
    <>
      <RemoteSelect<Partner>
        placeholder="Tìm đối tác theo mã, tên, MST"
        {...rest}
        selectedLabel={created && created.id === rest.value ? created.label : selectedLabel}
        suffixIcon={
          canCreate ? (
            <CreateSuffix title="Thêm đối tác" onClick={() => setCreating(true)} />
          ) : (
            rest.suffixIcon
          )
        }
        queryKey={`partners-${role ?? 'all'}`}
        fetch={(keyword) =>
          partnerApi.list({ page: 0, size: 30, companyId, keyword, role, status: 'ACTIVE' })
        }
        toOption={(partner) => ({ value: partner.id, label: `${partner.code} - ${partner.name}` })}
      />
      {canCreate && (
        <FormModal<Partial<Partner>>
          open={creating}
          width={900}
          title="Thêm đối tác"
          initialValues={{ partnerKind: 'ORGANIZATION', roles: [role ?? 'CUSTOMER'] }}
          fieldNames={PARTNER_FIELDS}
          onSubmit={save}
          onClose={() => setCreating(false)}
        >
          <PartnerForm editing={null} />
        </FormModal>
      )}
    </>
  );
}
