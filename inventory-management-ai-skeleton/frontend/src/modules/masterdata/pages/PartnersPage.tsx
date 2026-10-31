import { CheckCircleOutlined, EditOutlined, StopOutlined, UploadOutlined } from '@ant-design/icons';
import { App, Button, Card, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
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
import { partnerApi } from '../api';
import { ImportModal } from '../components/ImportModal';
import { PartnerForm } from '../components/PartnerForm';
import { PARTNER_FIELDS, partnerPayload } from '../components/partnerFields';
import { PARTNER_ROLES, toOptions } from '../labels';
import { useCompanyId } from '../lookups';
import type { Partner, PartnerRole } from '../types';

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
    const payload = partnerPayload(values);
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
        fieldNames={PARTNER_FIELDS}
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
