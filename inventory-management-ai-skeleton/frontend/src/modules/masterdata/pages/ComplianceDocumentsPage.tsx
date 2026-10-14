import { CheckCircleOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { complianceApi } from '../api';
import { PartnerSelect, ProductSelect } from '../components/EntitySelects';
import { COMPLIANCE_OWNERS } from '../labels';
import { useCompanyId } from '../lookups';
import type { ComplianceDocument, ComplianceOwnerType } from '../types';

const FIELDS = [
  'documentTypeId',
  'ownerType',
  'ownerId',
  'documentNo',
  'issuedDate',
  'expiryDate',
  'issuer',
  'note',
];
/** Đối tượng chọn được trên màn này; hồ sơ theo lô / serial gắn từ màn hình lô, serial của phân hệ kho. */
const OWNER_OPTIONS = (['PRODUCT', 'PARTNER', 'COMPANY'] as ComplianceOwnerType[]).map((value) => ({
  value,
  label: COMPLIANCE_OWNERS[value],
}));

interface FormValues extends Omit<Partial<ComplianceDocument>, 'issuedDate' | 'expiryDate'> {
  issuedDate?: Dayjs;
  expiryDate?: Dayjs;
}

function expiryTag(days: number | null) {
  if (days === null) {
    return <Tag>Không thời hạn</Tag>;
  }
  if (days < 0) {
    return <Tag color="red">Hết hạn {Math.abs(days)} ngày</Tag>;
  }
  if (days <= 60) {
    return <Tag color="orange">Còn {days} ngày</Tag>;
  }
  return <Tag color="green">Còn {days} ngày</Tag>;
}

/** Hồ sơ, giấy phép có hạn và cảnh báo sắp hết hạn (BR-MD-44, D-28). */
export function ComplianceDocumentsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const [documentTypeId, setDocumentTypeId] = useState<number>();
  const [expiringWithinDays, setExpiringWithinDays] = useState<number | null>(null);
  const [editing, setEditing] = useState<ComplianceDocument | 'new' | null>(null);
  const types = useQuery({
    queryKey: ['compliance-document-types', 'lookup', companyId],
    queryFn: () => complianceApi.types({ page: 0, size: 200, companyId, status: 'ACTIVE' }),
    enabled: companyId > 0,
  });
  const typeItems = types.data?.items ?? [];
  const { tableProps } = useServerTable('compliance-documents', complianceApi.documents, {
    companyId,
    documentTypeId,
    expiringWithinDays: expiringWithinDays ?? undefined,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['compliance-documents'] });
  const statusChange = useStatusChange(
    complianceApi.changeDocumentStatus,
    (item) => item.documentNo,
    refresh,
  );
  const canManage = can('compliance:manage');

  const save = async (values: FormValues, key: string) => {
    const dates = {
      issuedDate: values.issuedDate ? values.issuedDate.format('YYYY-MM-DD') : null,
      expiryDate: values.expiryDate ? values.expiryDate.format('YYYY-MM-DD') : null,
    };
    if (editing === 'new') {
      const ownerId = values.ownerType === 'COMPANY' ? companyId : values.ownerId;
      await complianceApi.createDocument({ ...values, ...dates, ownerId, companyId }, key);
      message.success('Đã thêm hồ sơ.');
    } else if (editing) {
      await complianceApi.updateDocument(editing.id, {
        documentNo: values.documentNo,
        issuer: values.issuer,
        note: values.note,
        ...dates,
        version: editing.version,
      });
      message.success('Đã lưu hồ sơ.');
    }
    await refresh();
  };

  const columns: TableColumnsType<ComplianceDocument> = [
    {
      title: 'Loại hồ sơ',
      dataIndex: 'documentTypeId',
      render: (id: number) => typeItems.find((type) => type.id === id)?.name ?? '',
    },
    { title: 'Số hồ sơ', dataIndex: 'documentNo', sorter: true, width: 180 },
    {
      title: 'Gắn với',
      width: 160,
      render: (_, document) => `${COMPLIANCE_OWNERS[document.ownerType]} #${document.ownerId}`,
    },
    {
      title: 'Hết hạn',
      dataIndex: 'expiryDate',
      sorter: true,
      width: 120,
      render: (value: string | null) => (value ? dayjs(value).format('DD/MM/YYYY') : ''),
    },
    { title: 'Còn lại', width: 150, render: (_, document) => expiryTag(document.daysToExpiry) },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, document) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing(document)} icon={<EditOutlined />}>
              Sửa
            </Button>
            <Button
              size="small"
              onClick={() => statusChange.open(document)}
              icon={document.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {document.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Hồ sơ, giấy phép
      </Typography.Title>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <Space wrap>
          <Select
            placeholder="Loại hồ sơ"
            allowClear
            value={documentTypeId}
            onChange={setDocumentTypeId}
            options={typeItems.map((type) => ({ value: type.id, label: type.name }))}
            style={{ width: 280 }}
          />
          <InputNumber
            addonBefore="Hết hạn trong"
            addonAfter="ngày"
            min={0}
            value={expiringWithinDays}
            onChange={(value) => setExpiringWithinDays(value)}
            style={{ width: 240 }}
          />
        </Space>
        {canManage && (
          <Button type="primary" onClick={() => setEditing('new')}>
            Thêm hồ sơ
          </Button>
        )}
      </div>
      <Table<ComplianceDocument> {...tableProps} columns={columns} scroll={{ x: 1100 }} />
      <FormModal<FormValues>
        open={editing !== null}
        title={editing === 'new' ? 'Thêm hồ sơ' : 'Sửa hồ sơ'}
        initialValues={
          editing && editing !== 'new'
            ? {
                ...editing,
                issuer: editing.issuer ?? undefined,
                note: editing.note ?? undefined,
                issuedDate: editing.issuedDate ? dayjs(editing.issuedDate) : undefined,
                expiryDate: editing.expiryDate ? dayjs(editing.expiryDate) : undefined,
              }
            : { ownerType: 'PRODUCT' }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <>
            <Form.Item
              name="documentTypeId"
              label="Loại hồ sơ"
              rules={[{ required: true, message: 'Chọn loại' }]}
            >
              <Select
                disabled={editing !== 'new'}
                options={typeItems
                  .filter((type) => type.appliesTo !== 'LOT' && type.appliesTo !== 'SERIAL')
                  .map((type) => ({ value: type.id, label: type.name }))}
                onChange={(id: number) =>
                  form.setFieldValue('ownerType', typeItems.find((type) => type.id === id)?.appliesTo)
                }
              />
            </Form.Item>
            <Form.Item name="ownerType" label="Gắn với" rules={[{ required: true }]}>
              <Select options={OWNER_OPTIONS} disabled />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {() =>
                editing === 'new' && form.getFieldValue('ownerType') === 'PRODUCT' ? (
                  <Form.Item
                    name="ownerId"
                    label="Hàng hóa"
                    rules={[{ required: true, message: 'Chọn hàng' }]}
                  >
                    <ProductSelect />
                  </Form.Item>
                ) : editing === 'new' && form.getFieldValue('ownerType') === 'PARTNER' ? (
                  <Form.Item
                    name="ownerId"
                    label="Đối tác"
                    rules={[{ required: true, message: 'Chọn đối tác' }]}
                  >
                    <PartnerSelect />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
            <Form.Item
              name="documentNo"
              label="Số hồ sơ"
              rules={[{ required: true, whitespace: true, message: 'Nhập số' }]}
            >
              <Input maxLength={100} />
            </Form.Item>
            <Space wrap>
              <Form.Item name="issuedDate" label="Ngày cấp">
                <DatePicker format="DD/MM/YYYY" />
              </Form.Item>
              <Form.Item name="expiryDate" label="Ngày hết hạn">
                <DatePicker format="DD/MM/YYYY" />
              </Form.Item>
            </Space>
            <Form.Item name="issuer" label="Nơi cấp">
              <Input maxLength={255} />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú">
              <Input maxLength={500} />
            </Form.Item>
          </>
        )}
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </Card>
  );
}
