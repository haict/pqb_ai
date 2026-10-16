import {
  CheckCircleOutlined,
  EditOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { formValues } from '../../../shared/forms';
import { UserSelect } from '../../system/components/UserSelect';
import { useMyBranches } from '../../system/lookups';
import { partnerApi, priceListApi } from '../api';
import { ImportModal } from '../components/ImportModal';
import { CUSTOMER_TYPES, PARTNER_ROLES, toOptions } from '../labels';
import { useCatalog, useCompanyId, useCountries } from '../lookups';
import type { Partner, PartnerRole } from '../types';

const FIELDS = [
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

function PartnerForm({ editing }: { editing: Partner | null }) {
  const companyId = useCompanyId();
  const groups = useCatalog('partner-groups');
  const countries = useCountries();
  const branches = useMyBranches();
  const form = Form.useFormInstance();
  const roles = Form.useWatch('roles', form) as PartnerRole[] | undefined;
  // Đơn mua lấy MST, địa chỉ người bán theo nhà cung cấp nên nhà cung cấp bắt buộc hai trường này
  const supplier = roles?.includes('SUPPLIER') ?? false;
  const salesLists = useQuery({
    queryKey: ['price-lists', 'sales-lookup', companyId],
    queryFn: () => priceListApi.list({ page: 0, size: 200, companyId, priceType: 'SALES', status: 'ACTIVE' }),
    enabled: companyId > 0,
  });

  return (
    <Tabs
      items={[
        {
          key: 'general',
          label: 'Thông tin chung',
          forceRender: true,
          children: (
            <>
              <Space wrap>
                <Form.Item
                  name="code"
                  label="Mã đối tác"
                  rules={
                    editing
                      ? []
                      : [
                          { required: true, message: 'Nhập mã' },
                          { pattern: /^[A-Za-z0-9_.-]{1,50}$/, message: 'Mã gồm 1-50 ký tự chữ, số, _ . -' },
                        ]
                  }
                >
                  <Input disabled={editing !== null} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item name="partnerKind" label="Loại" rules={[{ required: true }]}>
                  <Select
                    style={{ width: 140 }}
                    options={[
                      { value: 'ORGANIZATION', label: 'Tổ chức' },
                      { value: 'INDIVIDUAL', label: 'Cá nhân' },
                    ]}
                  />
                </Form.Item>
              </Space>
              <Form.Item
                name="name"
                label="Tên"
                rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
              >
                <Input maxLength={255} />
              </Form.Item>
              <Form.Item
                name="roles"
                label="Vai trò"
                rules={[{ required: true, message: 'Chọn ít nhất một vai trò' }]}
              >
                <Checkbox.Group options={toOptions(PARTNER_ROLES)} />
              </Form.Item>
              <Space wrap>
                <Form.Item name="shortName" label="Tên viết tắt">
                  <Input maxLength={100} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item
                  name="taxCode"
                  label="Mã số thuế"
                  rules={[
                    { required: supplier, message: 'Nhà cung cấp phải có MST' },
                    { pattern: /^[0-9]{10}(-[0-9]{3})?$/, message: '10 số hoặc 10 số-3 số' },
                  ]}
                >
                  <Input style={{ width: 180 }} />
                </Form.Item>
                <Form.Item name="partnerGroupId" label="Nhóm">
                  <Select allowClear options={groups.options} style={{ width: 200 }} />
                </Form.Item>
              </Space>
              <Form.Item
                name="address"
                label="Địa chỉ (hóa đơn)"
                rules={[{ required: supplier, whitespace: true, message: 'Nhà cung cấp phải có địa chỉ' }]}
              >
                <Input maxLength={500} />
              </Form.Item>
              <Space wrap>
                <Form.Item name="phone" label="Điện thoại">
                  <Input maxLength={30} style={{ width: 180 }} />
                </Form.Item>
                <Form.Item
                  name="email"
                  label="Email"
                  rules={[{ type: 'email', message: 'Email không hợp lệ' }]}
                >
                  <Input style={{ width: 240 }} />
                </Form.Item>
                <Form.Item name="countryCode" label="Quốc gia">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={countries.options}
                    style={{ width: 180 }}
                  />
                </Form.Item>
              </Space>
            </>
          ),
        },
        {
          key: 'terms',
          label: 'Điều khoản',
          forceRender: true,
          children: (
            <>
              <Typography.Text strong>Khách hàng</Typography.Text>
              <Space wrap style={{ marginTop: 8 }}>
                <Form.Item name="customerType" label="Loại khách">
                  <Select allowClear options={toOptions(CUSTOMER_TYPES)} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item name="salesUserId" label="Nhân viên phụ trách">
                  <UserSelect allowClear style={{ width: 260 }} />
                </Form.Item>
                <Form.Item name="managingBranchId" label="Chi nhánh phụ trách">
                  <Select allowClear options={branches.options} style={{ width: 220 }} />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item name="paymentTermDays" label="Số ngày nợ">
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item name="creditLimit" label="Hạn mức tín dụng">
                  <InputNumber min={0} style={{ width: 180 }} />
                </Form.Item>
                <Form.Item name="minRemainingShelfLifeDays" label="Hạn dùng còn lại tối thiểu (ngày)">
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item name="priceListId" label="Bảng giá">
                  <Select
                    allowClear
                    style={{ width: 220 }}
                    options={(salesLists.data?.items ?? []).map((list) => ({
                      value: list.id,
                      label: `${list.code} - ${list.name}`,
                    }))}
                  />
                </Form.Item>
              </Space>
              <Typography.Text strong>Nhà cung cấp</Typography.Text>
              <Space wrap style={{ marginTop: 8 }}>
                <Form.Item name="supplierPaymentTermDays" label="Số ngày thanh toán">
                  <InputNumber min={0} precision={0} />
                </Form.Item>
                <Form.Item
                  name="currencyCode"
                  label="Tiền tệ"
                  rules={[{ pattern: /^[A-Z]{3}$/, message: '3 chữ in hoa' }]}
                >
                  <Input maxLength={3} style={{ width: 100 }} />
                </Form.Item>
                <Form.Item name="distributedBrands" label="Hãng phân phối">
                  <Input maxLength={500} style={{ width: 300 }} />
                </Form.Item>
              </Space>
              <Typography.Text strong>Đại lý</Typography.Text>
              <Space wrap style={{ marginTop: 8 }}>
                <Form.Item name="agentDiscountPercent" label="Chiết khấu đại lý (%)">
                  <InputNumber min={0} max={100} />
                </Form.Item>
                <Form.Item name="agentRegion" label="Khu vực phân phối">
                  <Input maxLength={200} style={{ width: 240 }} />
                </Form.Item>
                <Form.Item name="agentCreditLimit" label="Hạn mức đại lý">
                  <InputNumber min={0} style={{ width: 180 }} />
                </Form.Item>
              </Space>
            </>
          ),
        },
        {
          key: 'contacts',
          label: 'Liên hệ & địa chỉ giao',
          forceRender: true,
          children: (
            <>
              <Typography.Text strong>Người liên hệ</Typography.Text>
              <Form.List name="contacts">
                {(fields, { add, remove }) => (
                  <>
                    {fields.map((field) => (
                      <Space key={field.key} wrap align="baseline" style={{ display: 'flex', marginTop: 8 }}>
                        <Form.Item
                          name={[field.name, 'name']}
                          rules={[{ required: true, message: 'Nhập tên' }]}
                        >
                          <Input placeholder="Họ tên" style={{ width: 180 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'position']}>
                          <Input placeholder="Chức vụ" style={{ width: 140 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'phone']}>
                          <Input placeholder="Điện thoại" style={{ width: 140 }} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'email']}
                          rules={[{ type: 'email', message: 'Email sai' }]}
                        >
                          <Input placeholder="Email" style={{ width: 180 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'primaryContact']} valuePropName="checked">
                          <Checkbox>Chính</Checkbox>
                        </Form.Item>
                        <MinusCircleOutlined onClick={() => remove(field.name)} />
                      </Space>
                    ))}
                    <Button
                      type="dashed"
                      icon={<PlusOutlined />}
                      onClick={() => add({ primaryContact: false })}
                    >
                      Thêm người liên hệ
                    </Button>
                  </>
                )}
              </Form.List>
              <div style={{ marginTop: 16 }}>
                <Typography.Text strong>Địa chỉ giao hàng</Typography.Text>
              </div>
              <Form.List name="addresses">
                {(fields, { add, remove }) => (
                  <>
                    {fields.map((field) => (
                      <Space key={field.key} wrap align="baseline" style={{ display: 'flex', marginTop: 8 }}>
                        <Form.Item
                          name={[field.name, 'address']}
                          rules={[{ required: true, message: 'Nhập địa chỉ' }]}
                        >
                          <Input placeholder="Địa chỉ" style={{ width: 300 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'receiverName']}>
                          <Input placeholder="Người nhận" style={{ width: 160 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'phone']}>
                          <Input placeholder="Điện thoại" style={{ width: 130 }} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'defaultAddress']} valuePropName="checked">
                          <Checkbox>Mặc định</Checkbox>
                        </Form.Item>
                        <MinusCircleOutlined onClick={() => remove(field.name)} />
                      </Space>
                    ))}
                    <Button
                      type="dashed"
                      icon={<PlusOutlined />}
                      onClick={() => add({ defaultAddress: false })}
                    >
                      Thêm địa chỉ giao
                    </Button>
                  </>
                )}
              </Form.List>
            </>
          ),
        },
      ]}
    />
  );
}

/** Đối tác nhiều vai trò (BR-MD-30..35), nhập Excel (BR-UX-05). */
export function PartnersPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<RecordStatus>();
  const [role, setRole] = useState<PartnerRole>();
  const [editing, setEditing] = useState<Partner | 'new' | null>(null);
  const [importing, setImporting] = useState(false);
  const { tableProps } = useServerTable('partners', partnerApi.list, { companyId, keyword, status, role });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['partners'] });
  const statusChange = useStatusChange(
    partnerApi.changeStatus,
    (item) => `${item.code} - ${item.name}`,
    refresh,
  );
  const canManage = can('partner:manage');

  const save = async (values: Partial<Partner>, key: string) => {
    const payload = {
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
    if (editing === 'new') {
      await partnerApi.create({ ...payload, companyId }, key);
      message.success('Đã thêm đối tác.');
    } else if (editing) {
      // Mã cũ MISA không còn nhập trên màn hình; gửi lại giá trị đang lưu để không bị xóa khi sửa.
      await partnerApi.update(editing.id, {
        ...payload,
        legacyCode: editing.legacyCode,
        version: editing.version,
      });
      message.success('Đã lưu đối tác.');
    }
    await refresh();
  };

  const openEdit = async (partner: Partner) => {
    try {
      setEditing(await partnerApi.get(partner.id));
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  const columns: TableColumnsType<Partner> = [
    { title: 'Mã', dataIndex: 'code', sorter: true, width: 140 },
    { title: 'Tên đối tác', dataIndex: 'name', sorter: true },
    {
      title: 'Vai trò',
      dataIndex: 'roles',
      width: 240,
      render: (roles: PartnerRole[]) => (
        <Space size={2} wrap>
          {roles.map((partnerRole) => (
            <Tag key={partnerRole}>{PARTNER_ROLES[partnerRole]}</Tag>
          ))}
        </Space>
      ),
    },
    { title: 'MST', dataIndex: 'taxCode', width: 140 },
    { title: 'Địa chỉ', dataIndex: 'address', width: 280, ellipsis: true },
    { title: 'Điện thoại', dataIndex: 'phone', width: 130 },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, partner) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => openEdit(partner)} icon={<EditOutlined />}>
              Sửa
            </Button>
            <Button
              size="small"
              danger={partner.status === 'ACTIVE'}
              onClick={() => statusChange.open(partner)}
              icon={partner.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {partner.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Đối tác
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo mã, tên, MST, điện thoại"
        onSearch={setKeyword}
        status={status}
        onStatusChange={setStatus}
        extraFilters={
          <>
            <Select
              placeholder="Vai trò"
              allowClear
              value={role}
              onChange={setRole}
              options={toOptions(PARTNER_ROLES)}
              style={{ width: 180 }}
            />
            {canManage && (
              <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>
                Nhập Excel
              </Button>
            )}
          </>
        }
        createLabel="Thêm đối tác"
        onCreate={canManage ? () => setEditing('new') : undefined}
      />
      <Table<Partner> {...tableProps} columns={columns} scroll={{ x: 1100 }} />
      <FormModal<Partial<Partner>>
        open={editing !== null}
        width={900}
        title={editing === 'new' ? 'Thêm đối tác' : 'Sửa đối tác'}
        initialValues={
          editing && editing !== 'new'
            ? (formValues(editing) as Partial<Partner>)
            : { partnerKind: 'ORGANIZATION', roles: ['CUSTOMER'] }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <PartnerForm editing={editing === 'new' ? null : editing} />
      </FormModal>
      <ImportModal
        open={importing}
        title="Nhập đối tác từ Excel"
        onDownloadTemplate={partnerApi.downloadTemplate}
        onImport={(file, key) => partnerApi.import(companyId, file, key)}
        onDone={refresh}
        onClose={() => setImporting(false)}
      />
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
