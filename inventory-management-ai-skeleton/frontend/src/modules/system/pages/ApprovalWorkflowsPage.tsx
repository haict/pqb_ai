import {
  CheckCircleOutlined,
  EditOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { ChangeStatusModal } from '../../../shared/components/ChangeStatusModal';
import { FormModal } from '../../../shared/components/FormModal';
import { StatusTag, type RecordStatus } from '../../../shared/components/StatusTag';
import { useStatusChange } from '../../../shared/components/useStatusChange';
import { approvalWorkflowApi, type ApprovalWorkflowInput } from '../api';
import { UserSelect } from '../components/UserSelect';
import { useActiveRoles, useCompanies, useDocumentTypes, useMyBranches } from '../lookups';
import type { ApprovalStep, ApprovalWorkflow } from '../types';
import { formatAmount } from '../../../shared/format';
import { BRANCH_VISIBLE } from '../../../shared/branch';

const FIELDS = ['documentType', 'name', 'branchId', 'minAmount', 'minDiscountPercent', 'priority', 'steps'];

function stepLabel(step: ApprovalStep) {
  return step.approverType === 'ROLE'
    ? (step.roleName ?? `Vai trò #${step.roleId}`)
    : (step.userName ?? `#${step.userId}`);
}

function WorkflowTable({ companyId }: { companyId: number }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const documentTypes = useDocumentTypes();
  const branches = useMyBranches();
  const roles = useActiveRoles();
  const [documentType, setDocumentType] = useState<string>();
  const [editing, setEditing] = useState<ApprovalWorkflow | 'new' | null>(null);
  const { tableProps } = useServerTable('approval-workflows', approvalWorkflowApi.list, {
    companyId,
    documentType,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['approval-workflows'] });
  const statusChange = useStatusChange(approvalWorkflowApi.changeStatus, (item) => item.name, refresh);
  const canManage = can('approval-workflow:manage');
  const branchOptions = branches.items
    .filter((branch) => branch.companyId === companyId)
    .map((branch) => ({ value: branch.id, label: `${branch.code} - ${branch.name}` }));

  const save = async (values: ApprovalWorkflowInput, key: string) => {
    const input = { ...values, branchId: values.branchId ?? null, steps: values.steps ?? [] };
    if (editing === 'new') {
      await approvalWorkflowApi.create({ ...input, companyId }, key);
      message.success('Đã tạo luồng duyệt.');
    } else if (editing) {
      await approvalWorkflowApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu luồng duyệt.');
    }
    await refresh();
  };

  const columns: TableColumnsType<ApprovalWorkflow> = [
    {
      title: 'Loại chứng từ',
      dataIndex: 'documentType',
      sorter: true,
      width: 220,
      render: (code: string) => documentTypes.nameOf(code),
    },
    { title: 'Tên luồng', dataIndex: 'name', sorter: true },
    {
      title: 'Áp dụng',
      width: 260,
      render: (_, workflow) => (
        <Space direction="vertical" size={0}>
          {BRANCH_VISIBLE && (
            <span>{workflow.branchId ? branches.nameOf(workflow.branchId) : 'Mọi chi nhánh'}</span>
          )}
          {workflow.minAmount !== null && (
            <Typography.Text type="secondary">
              Giá trị từ {formatAmount(Number(workflow.minAmount))}
            </Typography.Text>
          )}
          {workflow.minDiscountPercent !== null && (
            <Typography.Text type="secondary">Chiết khấu từ {workflow.minDiscountPercent}%</Typography.Text>
          )}
        </Space>
      ),
    },
    { title: 'Ưu tiên', dataIndex: 'priority', sorter: true, width: 90 },
    {
      title: 'Các bước duyệt',
      render: (_, workflow) =>
        workflow.steps.map((step, index) => `${index + 1}. ${stepLabel(step)}`).join(' → '),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: RecordStatus) => <StatusTag status={value} />,
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, workflow) =>
        canManage && (
          <Space>
            <Button size="small" onClick={() => setEditing(workflow)} icon={<EditOutlined />}>
              Sửa
            </Button>
            <Button
              size="small"
              danger={workflow.status === 'ACTIVE'}
              onClick={() => statusChange.open(workflow)}
              icon={workflow.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
            >
              {workflow.status === 'ACTIVE' ? 'Ngừng' : 'Kích hoạt'}
            </Button>
          </Space>
        ),
    },
  ];

  return (
    <>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <Select
          placeholder="Loại chứng từ"
          allowClear
          value={documentType}
          onChange={setDocumentType}
          options={documentTypes.options}
          style={{ width: 320 }}
        />
        {canManage && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Thêm luồng duyệt
          </Button>
        )}
      </div>
      <Table<ApprovalWorkflow> {...tableProps} columns={columns} scroll={{ x: 1300 }} />

      <FormModal<ApprovalWorkflowInput>
        open={editing !== null}
        width={720}
        title={editing === 'new' ? 'Thêm luồng duyệt' : 'Sửa luồng duyệt'}
        initialValues={
          editing && editing !== 'new'
            ? {
                documentType: editing.documentType,
                name: editing.name,
                branchId: editing.branchId ?? undefined,
                minAmount: editing.minAmount ?? undefined,
                minDiscountPercent: editing.minDiscountPercent ?? undefined,
                priority: editing.priority,
                steps: editing.steps.map((step) => ({
                  approverType: step.approverType,
                  roleId: step.roleId,
                  userId: step.userId,
                })),
              }
            : { priority: 0, steps: [{ approverType: 'ROLE' }] }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        <Form.Item
          name="documentType"
          label="Loại chứng từ"
          rules={[{ required: true, message: 'Chọn loại' }]}
        >
          <Select
            options={documentTypes.options}
            disabled={editing !== 'new'}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="Tên luồng"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên' }]}
        >
          <Input maxLength={200} />
        </Form.Item>
        <Space wrap>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            extra="Bỏ trống: mọi chi nhánh"
            hidden={!BRANCH_VISIBLE}
          >
            <Select allowClear options={branchOptions} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="minAmount" label="Giá trị từ" extra="Bỏ trống: không xét">
            <InputNumber min={0} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="minDiscountPercent" label="Chiết khấu từ (%)">
            <InputNumber min={0} max={100} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="priority"
            label="Ưu tiên"
            rules={[{ required: true }]}
            extra="Nhiều luồng cùng khớp: chọn ưu tiên cao nhất"
          >
            <InputNumber precision={0} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        <Typography.Text strong>Các bước duyệt (theo thứ tự)</Typography.Text>
        <Form.List name="steps">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field, index) => (
                <Space key={field.key} align="baseline" wrap style={{ display: 'flex', marginTop: 8 }}>
                  <Typography.Text>Bước {index + 1}</Typography.Text>
                  <Form.Item name={[field.name, 'approverType']} rules={[{ required: true }]}>
                    <Select
                      style={{ width: 140 }}
                      options={[
                        { value: 'ROLE', label: 'Theo vai trò' },
                        { value: 'USER', label: 'Người cụ thể' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item noStyle shouldUpdate>
                    {(form) =>
                      form.getFieldValue(['steps', field.name, 'approverType']) === 'USER' ? (
                        <Form.Item
                          name={[field.name, 'userId']}
                          rules={[{ required: true, message: 'Chọn người' }]}
                        >
                          <UserSelect style={{ width: 300 }} />
                        </Form.Item>
                      ) : (
                        <Form.Item
                          name={[field.name, 'roleId']}
                          rules={[{ required: true, message: 'Chọn vai trò' }]}
                        >
                          <Select
                            style={{ width: 300 }}
                            showSearch
                            optionFilterProp="label"
                            options={roles.map((role) => ({ value: role.id, label: role.name }))}
                          />
                        </Form.Item>
                      )
                    }
                  </Form.Item>
                  {fields.length > 1 && <MinusCircleOutlined onClick={() => remove(field.name)} />}
                </Space>
              ))}
              {fields.length < 10 && (
                <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ approverType: 'ROLE' })}>
                  Thêm bước
                </Button>
              )}
            </>
          )}
        </Form.List>
      </FormModal>
      <ChangeStatusModal {...statusChange.modalProps} />
    </>
  );
}

/** Luồng duyệt theo loại chứng từ, chi nhánh, ngưỡng; nhiều bước (BR-SYS-11, D-04). */
export function ApprovalWorkflowsPage() {
  const companies = useCompanies();
  const [selectedCompanyId, setCompanyId] = useState<number>();
  const companyId = selectedCompanyId ?? companies.items[0]?.id;

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Luồng duyệt
      </Typography.Title>
      {companies.items.length > 1 && (
        <Select
          placeholder="Công ty"
          value={companyId}
          onChange={setCompanyId}
          options={companies.options}
          style={{ width: 240, marginBottom: 16 }}
        />
      )}
      {companyId !== undefined && <WorkflowTable key={companyId} companyId={companyId} />}
    </Card>
  );
}
