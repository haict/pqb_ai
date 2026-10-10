import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Typography,
  App,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState, type ReactNode } from 'react';
import type { ListParams } from '../../../shared/api/useServerTable';
import { useServerTable } from '../../../shared/api/useServerTable';
import type { PageResponse } from '../../../shared/api/types';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import {
  catalogApi,
  complianceApi,
  countryApi,
  referenceItemApi,
  taxRateApi,
  type SimpleCatalogPath,
} from '../api';
import { COMPLIANCE_OWNERS, PRODUCT_TYPES, REFERENCE_CATEGORIES, toOptions } from '../labels';
import { useCompanyId, useCountries } from '../lookups';
import type { ComplianceDocumentType, ReferenceCategory } from '../types';

const CODE_RULES = [
  { required: true, message: 'Nhập mã' },
  { pattern: /^[A-Z0-9_.-]{1,30}$/, message: 'Mã gồm 1-30 ký tự A-Z, 0-9, _ . -' },
];
const NAME_RULES = [{ required: true, whitespace: true, message: 'Nhập tên' }];

interface Entity {
  id?: number;
  code: string;
  name: string;
  status: RecordStatus;
  version: number;
}

interface CrudTabProps<T extends Entity, F extends object> {
  queryKey: string;
  fetch: (params: ListParams & F) => Promise<PageResponse<T>>;
  filters: F;
  extraFilters?: ReactNode;
  columns: TableColumnsType<T>;
  create: (values: Record<string, unknown>, key: string) => Promise<unknown>;
  update: (item: T, values: Record<string, unknown>) => Promise<unknown>;
  changeStatus: (item: T, status: RecordStatus, reason: string) => Promise<unknown>;
  initialValues?: (item: T | null) => Record<string, unknown>;
  fields: (editing: T | null) => ReactNode;
  fieldNames: string[];
  label: string;
  rowKey?: string;
}

/** Bảng danh mục chuẩn: tìm kiếm, lọc trạng thái, phân trang server, thêm, sửa, ngừng hoạt động. */
function CrudTab<T extends Entity, F extends object>(props: CrudTabProps<T, F>) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [editing, setEditing] = useState<T | 'new' | null>(null);
  const { tableProps } = useServerTable(props.queryKey, props.fetch, { ...props.filters, keyword, status });
  const refresh = () => queryClient.invalidateQueries({ queryKey: [props.queryKey] });
  const statusChange = useStatusChange(props.changeStatus, (item) => `${item.code} - ${item.name}`, refresh);
  const canManage = can('catalog:manage');

  const save = async (values: Record<string, unknown>, key: string) => {
    if (editing === 'new') {
      await props.create(values, key);
      message.success(`Đã thêm ${props.label.toLowerCase()}.`);
    } else if (editing) {
      await props.update(editing, values);
      message.success('Đã lưu.');
    }
    await refresh();
  };

  const columns: TableColumnsType<T> = [
    ...props.columns,
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, item) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing(item)}>
              Sửa
            </Button>
            <Button size="small" danger={item.status === 'ACTIVE'} onClick={() => statusChange.open(item)}>
              {item.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];
  const current = editing === 'new' ? null : editing;

  return (
    <>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={props.extraFilters}
        createLabel={`Thêm ${props.label.toLowerCase()}`}
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<T> {...tableProps} rowKey={props.rowKey ?? 'id'} columns={columns} scroll={{ x: 800 }} />
      <FormModal<Record<string, unknown>>
        open={editing !== null}
        title={editing === 'new' ? `Thêm ${props.label.toLowerCase()}` : `Sửa ${props.label.toLowerCase()}`}
        initialValues={
          props.initialValues
            ? props.initialValues(current)
            : current
              ? ({ ...current } as Record<string, unknown>)
              : {}
        }
        fieldNames={props.fieldNames}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {props.fields(current)}
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </>
  );
}

const BASE_COLUMNS = [
  { title: 'Mã', dataIndex: 'code', sorter: true, width: 140 },
  { title: 'Tên', dataIndex: 'name', sorter: true },
];

function codeNameFields(editing: Entity | null) {
  return (
    <>
      <Form.Item name="code" label="Mã" rules={editing ? [] : CODE_RULES}>
        <Input disabled={editing !== null} maxLength={30} />
      </Form.Item>
      <Form.Item name="name" label="Tên" rules={NAME_RULES}>
        <Input maxLength={200} />
      </Form.Item>
    </>
  );
}

function SimpleCatalogTab({ path, label }: { path: SimpleCatalogPath; label: string }) {
  const companyId = useCompanyId();
  const countries = useCountries();
  const withCountry = path === 'manufacturers';
  return (
    <CrudTab
      queryKey={path}
      label={label}
      fetch={(params) => catalogApi.list(path, params)}
      filters={{ companyId }}
      columns={[
        ...BASE_COLUMNS,
        ...(withCountry ? [{ title: 'Quốc gia', dataIndex: 'countryCode', width: 110 }] : []),
      ]}
      create={(values, key) =>
        catalogApi.create(path, { ...(values as { code: string; name: string }), companyId }, key)
      }
      update={(item, values) =>
        catalogApi.update(path, item.id as number, { ...(values as { name: string }), version: item.version })
      }
      changeStatus={(item, status, reason) => catalogApi.changeStatus(path, item as never, status, reason)}
      fieldNames={['code', 'name', 'countryCode']}
      fields={(editing) => (
        <>
          {codeNameFields(editing)}
          {withCountry && (
            <Form.Item name="countryCode" label="Quốc gia">
              <Select allowClear showSearch optionFilterProp="label" options={countries.options} />
            </Form.Item>
          )}
        </>
      )}
    />
  );
}

function CountriesTab() {
  return (
    <CrudTab
      queryKey="countries"
      label="Quốc gia"
      rowKey="code"
      fetch={countryApi.list}
      filters={{}}
      columns={BASE_COLUMNS}
      create={(values, key) => countryApi.create(values as { code: string; name: string }, key)}
      update={(item, values) =>
        countryApi.update(item.code, { name: values.name as string, version: item.version })
      }
      changeStatus={(item, status, reason) => countryApi.changeStatus(item as never, status, reason)}
      fieldNames={['code', 'name']}
      fields={(editing) => (
        <>
          <Form.Item
            name="code"
            label="Mã ISO (2 chữ)"
            rules={editing ? [] : [{ required: true, pattern: /^[A-Z]{2}$/, message: 'Mã gồm 2 chữ in hoa' }]}
          >
            <Input disabled={editing !== null} maxLength={2} />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={NAME_RULES}>
            <Input maxLength={100} />
          </Form.Item>
        </>
      )}
    />
  );
}

interface TaxRow extends Entity {
  rate: number;
  validFrom: string;
  validTo: string | null;
}

function TaxRatesTab() {
  const companyId = useCompanyId();
  const toPayload = (values: Record<string, unknown>) => ({
    ...values,
    validFrom: (values.validFrom as Dayjs).format('YYYY-MM-DD'),
    validTo: values.validTo ? (values.validTo as Dayjs).format('YYYY-MM-DD') : null,
  });
  return (
    <CrudTab<TaxRow, { companyId: number }>
      queryKey="tax-rates"
      label="Thuế suất"
      fetch={taxRateApi.list as never}
      filters={{ companyId }}
      columns={[
        ...BASE_COLUMNS,
        { title: 'Thuế suất (%)', dataIndex: 'rate', width: 120 },
        {
          title: 'Từ ngày',
          dataIndex: 'validFrom',
          width: 120,
          render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
        },
        {
          title: 'Đến ngày',
          dataIndex: 'validTo',
          width: 120,
          render: (v: string | null) => (v ? dayjs(v).format('DD/MM/YYYY') : ''),
        },
      ]}
      initialValues={(item) =>
        item
          ? {
              ...item,
              validFrom: dayjs(item.validFrom),
              validTo: item.validTo ? dayjs(item.validTo) : undefined,
            }
          : { validFrom: dayjs().startOf('year') }
      }
      create={(values, key) => taxRateApi.create({ ...toPayload(values), companyId }, key)}
      update={(item, values) =>
        taxRateApi.update(item.id as number, { ...toPayload(values), version: item.version })
      }
      changeStatus={(item, status, reason) => taxRateApi.changeStatus(item as never, status, reason)}
      fieldNames={['code', 'name', 'rate', 'validFrom', 'validTo']}
      fields={(editing) => (
        <>
          {codeNameFields(editing)}
          <Form.Item
            name="rate"
            label="Thuế suất (%)"
            rules={[{ required: true, message: 'Nhập thuế suất' }]}
          >
            <InputNumber min={0} max={100} style={{ width: 160 }} />
          </Form.Item>
          <Space>
            <Form.Item name="validFrom" label="Từ ngày" rules={[{ required: true, message: 'Chọn ngày' }]}>
              <DatePicker format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="validTo" label="Đến ngày">
              <DatePicker format="DD/MM/YYYY" />
            </Form.Item>
          </Space>
        </>
      )}
    />
  );
}

function ReferenceItemsTab() {
  const companyId = useCompanyId();
  const [category, setCategory] = useState<ReferenceCategory>();
  return (
    <CrudTab
      queryKey="reference-items"
      label="Mục danh mục phụ"
      fetch={referenceItemApi.list as never}
      filters={{ companyId, category }}
      extraFilters={
        <Select
          placeholder="Loại danh mục"
          allowClear
          value={category}
          onChange={setCategory}
          options={toOptions(REFERENCE_CATEGORIES)}
          style={{ width: 280 }}
        />
      }
      columns={[
        {
          title: 'Loại',
          dataIndex: 'category',
          width: 260,
          render: (value: ReferenceCategory) => REFERENCE_CATEGORIES[value],
        },
        ...BASE_COLUMNS,
        { title: 'Giá trị số', dataIndex: 'numericValue', width: 110 },
      ]}
      initialValues={(item) => (item ? { ...item } : { category })}
      create={(values, key) => referenceItemApi.create({ ...values, companyId }, key)}
      update={(item, values) =>
        referenceItemApi.update(item.id as number, {
          name: values.name,
          numericValue: values.numericValue ?? null,
          version: item.version,
        })
      }
      changeStatus={(item, status, reason) => referenceItemApi.changeStatus(item as never, status, reason)}
      fieldNames={['category', 'code', 'name', 'numericValue']}
      fields={(editing) => (
        <>
          <Form.Item name="category" label="Loại danh mục" rules={[{ required: true, message: 'Chọn loại' }]}>
            <Select options={toOptions(REFERENCE_CATEGORIES)} disabled={editing !== null} />
          </Form.Item>
          {codeNameFields(editing)}
          <Form.Item name="numericValue" label="Giá trị số" extra="Ví dụ số ngày của điều khoản thanh toán">
            <InputNumber style={{ width: 160 }} />
          </Form.Item>
        </>
      )}
    />
  );
}

function ComplianceTypesTab() {
  const companyId = useCompanyId();
  return (
    <CrudTab<ComplianceDocumentType, { companyId: number }>
      queryKey="compliance-document-types"
      label="Loại hồ sơ"
      fetch={complianceApi.types as never}
      filters={{ companyId }}
      columns={[
        ...BASE_COLUMNS,
        {
          title: 'Gắn với',
          dataIndex: 'appliesTo',
          width: 120,
          render: (value: keyof typeof COMPLIANCE_OWNERS) => COMPLIANCE_OWNERS[value],
        },
        { title: 'Có hạn', dataIndex: 'hasExpiry', width: 90, render: (v: boolean) => (v ? 'Có' : 'Không') },
      ]}
      initialValues={(item) => (item ? { ...item } : { appliesTo: 'PRODUCT', hasExpiry: true })}
      create={(values, key) => complianceApi.createType({ ...values, companyId }, key)}
      update={(item, values) => complianceApi.updateType(item.id, { ...values, version: item.version })}
      changeStatus={(item, status, reason) => complianceApi.changeTypeStatus(item, status, reason)}
      fieldNames={['code', 'name', 'appliesTo', 'hasExpiry', 'requiredProductTypes']}
      fields={(editing) => (
        <>
          {codeNameFields(editing)}
          <Form.Item name="appliesTo" label="Gắn với" rules={[{ required: true }]}>
            <Select options={toOptions(COMPLIANCE_OWNERS)} />
          </Form.Item>
          <Form.Item name="hasExpiry" label="Có ngày hết hạn" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="requiredProductTypes" label="Bắt buộc với loại hàng">
            <Select mode="multiple" allowClear options={toOptions(PRODUCT_TYPES)} />
          </Form.Item>
        </>
      )}
    />
  );
}

/** Danh mục phụ của phân hệ M02 (BR-MD-03, 20, 31, 42, 44, 45, 46). */
export function CatalogsPage() {
  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Danh mục chung
      </Typography.Title>
      <Tabs
        destroyOnHidden
        items={[
          {
            key: 'units',
            label: 'Đơn vị tính',
            children: <SimpleCatalogTab path="units" label="Đơn vị tính" />,
          },
          {
            key: 'brands',
            label: 'Nhãn hiệu',
            children: <SimpleCatalogTab path="brands" label="Nhãn hiệu" />,
          },
          {
            key: 'manufacturers',
            label: 'Nhà sản xuất',
            children: <SimpleCatalogTab path="manufacturers" label="Nhà sản xuất" />,
          },
          { key: 'countries', label: 'Xuất xứ', children: <CountriesTab /> },
          {
            key: 'partner-groups',
            label: 'Nhóm đối tác',
            children: <SimpleCatalogTab path="partner-groups" label="Nhóm đối tác" />,
          },
          { key: 'tax-rates', label: 'Thuế suất', children: <TaxRatesTab /> },
          { key: 'reference-items', label: 'Danh mục phụ', children: <ReferenceItemsTab /> },
          { key: 'compliance-types', label: 'Loại hồ sơ', children: <ComplianceTypesTab /> },
        ]}
      />
    </Card>
  );
}
