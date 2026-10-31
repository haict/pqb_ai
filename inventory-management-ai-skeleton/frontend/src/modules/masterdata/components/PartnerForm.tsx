import { MinusCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Tabs,
  Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { BRANCH_VISIBLE } from '../../../shared/branch';
import { UserSelect } from '../../system/components/UserSelect';
import { useMyBranches } from '../../system/lookups';
import { partnerApi, priceListApi } from '../api';
import { CUSTOMER_TYPES, PARTNER_ROLES, toOptions } from '../labels';
import { useCatalog, useCompanyId, useCountries } from '../lookups';
import type { Partner, PartnerRole } from '../types';
import { taxCodeFill } from './partnerFields';

export function PartnerForm({ editing }: { editing: Partner | null }) {
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
  const { message } = App.useApp();
  const [lookingUp, setLookingUp] = useState(false);
  const [lookupNotes, setLookupNotes] = useState<string[]>([]);

  // D-66: tra cứu tên, địa chỉ theo MST; chỉ điền ô trống, người dùng xem lại trước khi lưu.
  const lookupTaxCode = async () => {
    try {
      await form.validateFields(['taxCode']);
    } catch {
      return;
    }
    const taxCode = String(form.getFieldValue('taxCode') ?? '').trim();
    if (!taxCode) {
      form.setFields([{ name: 'taxCode', errors: ['Nhập mã số thuế để tra cứu'] }]);
      return;
    }
    setLookingUp(true);
    try {
      const fill = taxCodeFill(
        form.getFieldsValue(['name', 'shortName', 'address', 'phone']),
        await partnerApi.lookupTaxCode(taxCode),
      );
      form.setFieldsValue(fill.values);
      setLookupNotes(fill.notes);
      message.success(
        Object.keys(fill.values).length > 0
          ? 'Đã điền thông tin theo mã số thuế, vui lòng kiểm tra lại.'
          : 'Đã tra cứu; các ô đã có dữ liệu được giữ nguyên.',
      );
    } catch (error) {
      setLookupNotes([]);
      message.error(toApiError(error).message);
    } finally {
      setLookingUp(false);
    }
  };

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
                  <Input.Search
                    style={{ width: 230 }}
                    enterButton={<SearchOutlined aria-label="Tra cứu mã số thuế" />}
                    loading={lookingUp}
                    onSearch={() => void lookupTaxCode()}
                    onChange={() => setLookupNotes([])}
                  />
                </Form.Item>
                <Form.Item name="partnerGroupId" label="Nhóm">
                  <Select allowClear options={groups.options} style={{ width: 200 }} />
                </Form.Item>
              </Space>
              {lookupNotes.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="Khác với dữ liệu đang nhập (giữ nguyên ô đã nhập)"
                  description={lookupNotes.map((note) => (
                    <div key={note}>{note}</div>
                  ))}
                />
              )}
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
                <Form.Item name="managingBranchId" label="Chi nhánh phụ trách" hidden={!BRANCH_VISIBLE}>
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
