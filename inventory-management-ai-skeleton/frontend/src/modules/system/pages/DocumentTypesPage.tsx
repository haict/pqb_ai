import { App, Button, Card, Form, Select, Switch, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { documentTypeApi } from '../api';
import type { ApprovalMode, DocumentType } from '../types';

const MODES: { value: ApprovalMode; label: string; color: string }[] = [
  { value: 'NONE', label: 'Không duyệt', color: 'default' },
  { value: 'CONFIGURED', label: 'Duyệt khi có luồng khớp', color: 'blue' },
  { value: 'REQUIRED', label: 'Bắt buộc duyệt', color: 'red' },
];
const FIELDS = ['approvalMode', 'selfApprovalAllowed'];

interface Values {
  approvalMode: ApprovalMode;
  selfApprovalAllowed: boolean;
}

/** Loại chứng từ: chế độ duyệt (BR-GEN-05) và cho phép người lập tự duyệt (BR-SYS-14). */
export function DocumentTypesPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<DocumentType | null>(null);
  const query = useQuery({ queryKey: ['document-types'], queryFn: documentTypeApi.list });

  const save = async (values: Values) => {
    if (!editing) {
      return;
    }
    await documentTypeApi.update(editing.code, { ...values, version: editing.version });
    message.success('Đã lưu cấu hình loại chứng từ.');
    await queryClient.invalidateQueries({ queryKey: ['document-types'] });
  };

  const columns: TableColumnsType<DocumentType> = [
    { title: 'Mã', dataIndex: 'code', width: 110 },
    { title: 'Tên loại chứng từ', dataIndex: 'name' },
    { title: 'Phân hệ', dataIndex: 'module', width: 130 },
    {
      title: 'Chế độ duyệt',
      dataIndex: 'approvalMode',
      width: 220,
      render: (mode: ApprovalMode) => {
        const item = MODES.find((candidate) => candidate.value === mode);
        return <Tag color={item?.color}>{item?.label}</Tag>;
      },
    },
    {
      title: 'Người lập tự duyệt',
      dataIndex: 'selfApprovalAllowed',
      width: 160,
      render: (allowed: boolean) => (allowed ? 'Cho phép' : 'Không cho phép'),
    },
    {
      title: 'Thao tác',
      width: 100,
      render: (_, type) =>
        can('approval-workflow:manage') && (
          <Button size="small" onClick={() => setEditing(type)}>
            Sửa
          </Button>
        ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Loại chứng từ
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        Loại chứng từ do từng phân hệ đăng ký. "Bắt buộc duyệt" mà chưa có luồng duyệt khớp thì chứng từ không
        gửi duyệt được.
      </Typography.Paragraph>
      <Table<DocumentType>
        rowKey="code"
        loading={query.isFetching}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        scroll={{ x: 900 }}
      />
      <FormModal<Values>
        open={editing !== null}
        title={editing ? `Cấu hình: ${editing.name}` : ''}
        initialValues={
          editing
            ? { approvalMode: editing.approvalMode, selfApprovalAllowed: editing.selfApprovalAllowed }
            : undefined
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item name="approvalMode" label="Chế độ duyệt" rules={[{ required: true }]}>
          <Select options={MODES} />
        </Form.Item>
        <Form.Item
          name="selfApprovalAllowed"
          label="Cho phép người lập tự duyệt"
          valuePropName="checked"
          extra="Tắt để áp dụng tách biệt nhiệm vụ (BR-SYS-14)."
        >
          <Switch />
        </Form.Item>
      </FormModal>
    </Card>
  );
}
