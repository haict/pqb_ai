import { EditOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { documentNumberFormatApi, type DocumentNumberFormatInput } from '../api';
import { FROM_BEGINNING, sampleNumber } from '../documentNumber';
import { useCompanies, useMyBranches } from '../lookups';
import type { DocumentNumberFormat, DocumentNumberFormatStatus, NumberResetCycle } from '../types';

const FIELDS = ['documentType', 'prefix', 'sequenceLength', 'resetCycle'];

const CYCLES: { value: NumberResetCycle; label: string }[] = [
  { value: 'YEARLY', label: 'Theo năm (YYYY)' },
  { value: 'MONTHLY', label: 'Theo tháng (YYYYMM)' },
];

const STATUSES: Record<DocumentNumberFormatStatus, { label: string; color: string }> = {
  CURRENT: { label: 'Đang áp dụng', color: 'green' },
  SCHEDULED: { label: 'Đặt trước', color: 'blue' },
  SUPERSEDED: { label: 'Đã thay thế', color: 'default' },
};

type FormatValues = Omit<DocumentNumberFormatInput, 'companyId'>;

function FormatTable({ companyId }: { companyId: number }) {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const branches = useMyBranches();
  const branchCode = branches.items.find((branch) => branch.companyId === companyId)?.code ?? 'CN';
  const [documentType, setDocumentType] = useState('');
  const [editing, setEditing] = useState<DocumentNumberFormat | 'new' | null>(null);
  const { tableProps } = useServerTable('document-number-formats', documentNumberFormatApi.list, {
    companyId,
    documentType,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['document-number-formats'] });
  const canUpdate = can('document-number:update');

  const save = async (values: FormatValues, key: string) => {
    const saved = await documentNumberFormatApi.schedule({ ...values, companyId }, key);
    message.success(
      saved.status === 'SCHEDULED'
        ? `Đã đặt cấu hình, có hiệu lực từ ${dayjs(saved.effectiveFrom).format('DD/MM/YYYY')}.`
        : 'Đã lưu cấu hình, có hiệu lực ngay.',
    );
    await refresh();
  };

  const remove = async (format: DocumentNumberFormat) => {
    try {
      await documentNumberFormatApi.remove(format.id);
      message.success('Đã hủy cấu hình đặt trước.');
      await refresh();
    } catch (error) {
      message.error(toApiError(error).message);
    }
  };

  const columns: TableColumnsType<DocumentNumberFormat> = [
    { title: 'Loại chứng từ', dataIndex: 'documentType', sorter: true, width: 150 },
    { title: 'Tiền tố', dataIndex: 'prefix', width: 100 },
    { title: 'Độ dài số', dataIndex: 'sequenceLength', width: 100 },
    {
      title: 'Đặt lại số',
      dataIndex: 'resetCycle',
      width: 160,
      render: (value: NumberResetCycle) => CYCLES.find((cycle) => cycle.value === value)?.label,
    },
    {
      title: 'Hiệu lực từ',
      dataIndex: 'effectiveFrom',
      sorter: true,
      width: 130,
      render: (value: string) =>
        value === FROM_BEGINNING ? 'Ngay từ đầu' : dayjs(value).format('DD/MM/YYYY'),
    },
    {
      title: 'Ví dụ',
      width: 210,
      render: (_, format) => <Typography.Text code>{sampleNumber(format, branchCode)}</Typography.Text>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: DocumentNumberFormatStatus) => (
        <Tag color={STATUSES[value].color}>{STATUSES[value].label}</Tag>
      ),
    },
    {
      title: 'Thao tác',
      width: 170,
      render: (_, format) =>
        canUpdate &&
        format.status !== 'SUPERSEDED' && (
          <Space>
            <Button size="small" onClick={() => setEditing(format)} icon={<EditOutlined />}>
              Sửa
            </Button>
            {format.status === 'SCHEDULED' && (
              <Popconfirm
                title="Hủy cấu hình đặt trước?"
                okText="Hủy cấu hình"
                cancelText="Không"
                onConfirm={() => remove(format)}
              >
                <Button size="small" danger>
                  Hủy
                </Button>
              </Popconfirm>
            )}
          </Space>
        ),
    },
  ];

  return (
    <>
      <ListToolbar
        searchPlaceholder="Tìm theo loại chứng từ"
        onSearch={setDocumentType}
        createLabel="Đặt cấu hình"
        onCreate={canUpdate ? () => setEditing('new') : undefined}
      />
      <Table<DocumentNumberFormat> {...tableProps} columns={columns} scroll={{ x: 1100 }} />

      <FormModal<FormatValues>
        open={editing !== null}
        title={editing === 'new' ? 'Đặt cấu hình số chứng từ' : 'Sửa cấu hình số chứng từ'}
        initialValues={
          editing && editing !== 'new'
            ? {
                documentType: editing.documentType,
                prefix: editing.prefix,
                sequenceLength: editing.sequenceLength,
                resetCycle: editing.resetCycle,
              }
            : { sequenceLength: 6, resetCycle: 'YEARLY' }
        }
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="Loại chứng từ đã cấp số: thay đổi chỉ có hiệu lực từ chu kỳ sau (đầu tháng sau nếu đặt lại theo tháng, đầu năm sau nếu theo năm hoặc đổi chu kỳ). Chưa cấp số: có hiệu lực ngay."
            />
            <Form.Item
              name="documentType"
              label="Mã loại chứng từ"
              rules={[
                { required: true, message: 'Nhập mã loại' },
                { pattern: /^[A-Z0-9_]{1,30}$/, message: 'Mã loại gồm 1-30 ký tự A-Z, 0-9, _' },
              ]}
            >
              <Input disabled={editing !== 'new'} maxLength={30} />
            </Form.Item>
            <Form.Item
              name="prefix"
              label="Tiền tố"
              rules={[
                { required: true, message: 'Nhập tiền tố' },
                { pattern: /^[A-Z0-9]{1,10}$/, message: 'Tiền tố gồm 1-10 ký tự A-Z, 0-9' },
              ]}
            >
              <Input maxLength={10} />
            </Form.Item>
            <Form.Item
              name="sequenceLength"
              label="Số chữ số của số thứ tự"
              rules={[{ required: true, message: 'Nhập độ dài' }]}
            >
              <InputNumber min={3} max={10} precision={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="resetCycle" label="Đặt lại số thứ tự" rules={[{ required: true }]}>
              <Select options={CYCLES} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {() => {
                const values = form.getFieldsValue() as Partial<FormatValues>;
                return values.prefix && values.sequenceLength && values.resetCycle ? (
                  <Typography.Text type="secondary">
                    Ví dụ:{' '}
                    <Typography.Text code>
                      {sampleNumber(
                        {
                          prefix: values.prefix,
                          sequenceLength: values.sequenceLength,
                          resetCycle: values.resetCycle,
                          effectiveFrom: FROM_BEGINNING,
                        },
                        branchCode,
                      )}
                    </Typography.Text>
                  </Typography.Text>
                ) : null;
              }}
            </Form.Item>
          </>
        )}
      </FormModal>
    </>
  );
}

/** Cấu hình số chứng từ theo loại chứng từ (BR-SYS-10, D-03). */
export function DocumentNumberFormatsPage() {
  const companies = useCompanies();
  const [selectedCompanyId, setCompanyId] = useState<number>();
  const companyId = selectedCompanyId ?? companies.items[0]?.id;

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Số chứng từ
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        Định dạng: TIỀN TỐ-CHI NHÁNH-KỲ-SỐ THỨ TỰ. Loại chứng từ chưa cấu hình dùng định dạng mặc định
        LOẠI-CHI NHÁNH-YYYY-6 số.
      </Typography.Paragraph>
      {companies.items.length > 1 && (
        <Select
          placeholder="Công ty"
          value={companyId}
          onChange={setCompanyId}
          options={companies.options}
          style={{ width: 240, marginBottom: 16 }}
        />
      )}
      {companyId !== undefined && <FormatTable key={companyId} companyId={companyId} />}
    </Card>
  );
}
