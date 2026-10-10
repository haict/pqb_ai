import { PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Popconfirm,
  Select,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useAuth, useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { formatDateTime } from '../../../shared/format';
import { delegationApi } from '../api';
import { UserSelect } from '../components/UserSelect';
import { useDocumentTypes } from '../lookups';
import type { Delegation } from '../types';

const FIELDS = ['delegateUserId', 'documentType', 'period', 'reason'];
const NO_FILTERS = {};

interface Values {
  delegateUserId: number;
  documentType?: string;
  period: [Dayjs, Dayjs];
  reason?: string;
}

/** Ủy quyền duyệt có thời hạn (BR-SYS-11). */
export function DelegationsPage() {
  const can = useCan();
  const { user } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const documentTypes = useDocumentTypes();
  const [creating, setCreating] = useState(false);
  const { tableProps } = useServerTable('approval-delegations', delegationApi.list, NO_FILTERS);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['approval-delegations'] });

  const save = async (values: Values, key: string) => {
    await delegationApi.create(
      {
        delegateUserId: values.delegateUserId,
        documentType: values.documentType,
        validFrom: values.period[0].toISOString(),
        validTo: values.period[1].toISOString(),
        reason: values.reason,
      },
      key,
    );
    message.success('Đã ủy quyền duyệt.');
    await refresh();
  };

  const revoke = async (delegation: Delegation) => {
    try {
      await delegationApi.revoke(delegation.id);
      message.success('Đã thu hồi ủy quyền.');
      await refresh();
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  const columns: TableColumnsType<Delegation> = [
    { title: 'Người ủy quyền', dataIndex: 'delegatorUsername', width: 200 },
    { title: 'Người được ủy quyền', dataIndex: 'delegateUsername', width: 200 },
    {
      title: 'Loại chứng từ',
      dataIndex: 'documentType',
      render: (code: string | null) => (code ? documentTypes.nameOf(code) : 'Mọi loại'),
    },
    {
      title: 'Thời hạn',
      width: 290,
      render: (_, delegation) =>
        `${formatDateTime(delegation.validFrom)} - ${formatDateTime(delegation.validTo)}`,
    },
    {
      title: 'Trạng thái',
      width: 140,
      render: (_, delegation) =>
        delegation.status === 'REVOKED' ? (
          <Tag>Đã thu hồi</Tag>
        ) : delegation.effective ? (
          <Tag color="green">Đang hiệu lực</Tag>
        ) : (
          <Tag color="gold">Ngoài thời hạn</Tag>
        ),
    },
    { title: 'Lý do', dataIndex: 'reason' },
    {
      title: 'Thao tác',
      width: 110,
      render: (_, delegation) =>
        delegation.status === 'ACTIVE' &&
        (delegation.delegatorUserId === user?.id || can('approval-workflow:manage')) && (
          <Popconfirm
            title="Thu hồi ủy quyền này?"
            okText="Thu hồi"
            cancelText="Không"
            onConfirm={() => revoke(delegation)}
          >
            <Button size="small" danger>
              Thu hồi
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Ủy quyền duyệt
      </Typography.Title>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
          Ủy quyền duyệt
        </Button>
      </div>
      <Table<Delegation> {...tableProps} columns={columns} scroll={{ x: 1200 }} />
      <FormModal<Values>
        open={creating}
        title="Ủy quyền phần duyệt của tôi"
        initialValues={{ period: [dayjs(), dayjs().add(7, 'day')] }}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setCreating(false)}
      >
        <Form.Item
          name="delegateUserId"
          label="Người được ủy quyền"
          rules={[{ required: true, message: 'Chọn người được ủy quyền' }]}
        >
          <UserSelect />
        </Form.Item>
        <Form.Item name="documentType" label="Loại chứng từ" extra="Bỏ trống: mọi loại chứng từ">
          <Select allowClear options={documentTypes.options} showSearch optionFilterProp="label" />
        </Form.Item>
        <Form.Item name="period" label="Thời hạn" rules={[{ required: true, message: 'Chọn thời hạn' }]}>
          <DatePicker.RangePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="reason" label="Lý do">
          <Input maxLength={500} />
        </Form.Item>
      </FormModal>
    </Card>
  );
}
