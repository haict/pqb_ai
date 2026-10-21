import { UploadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, DatePicker, Form, Select, Table, Typography, type TableColumnsType } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { useCompanyId, useWarehouses } from '../../masterdata/lookups';
import { documentApi } from '../api';
import { DocumentFormFields } from '../components/DocumentForm';
import { DOCUMENT_FIELDS, toDocumentInput, type DocumentFormValues } from '../components/documentFormValues';
import {
  CREATABLE_TYPES,
  DOCUMENT_STATUSES,
  DOCUMENT_TYPE_PERMISSION,
  DOCUMENT_TYPES,
  OPENING_SUB_TYPE,
  formatDate,
  formatNumber,
  statusLabel,
} from '../labels';
import { DocumentStatusTag } from '../components/StatusTags';
import type {
  InventoryDocumentStatus,
  InventoryDocumentSummary,
  InventoryDocumentType,
  OpeningStockImportResult,
} from '../types';
import { ImportModal } from '../../masterdata/components/ImportModal';

/**
 * Danh sách chứng từ kho, lọc ở server; lập chứng từ mới theo loại người dùng có quyền.
 * {@code opening}: màn hình tồn kho đầu kỳ (BR-MIG-02), chỉ phiếu nhập loại tồn đầu kỳ.
 */
export function InventoryDocumentsPage({ opening = false }: { opening?: boolean }) {
  const can = useCan();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const companyId = useCompanyId();
  const warehouses = useWarehouses();
  const [keyword, setKeyword] = useState<string>();
  const [documentType, setDocumentType] = useState<InventoryDocumentType>();
  const [status, setStatus] = useState<InventoryDocumentStatus>();
  const [warehouseId, setWarehouseId] = useState<number>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importDate, setImportDate] = useState<Dayjs>(dayjs());
  const queryClient = useQueryClient();
  const { tableProps } = useServerTable('inventory-documents', documentApi.list, {
    companyId,
    keyword,
    documentType: opening ? 'PNK' : documentType,
    subType: opening ? OPENING_SUB_TYPE : undefined,
    status,
    warehouseId,
    dateFrom: range?.[0]?.format('YYYY-MM-DD'),
    dateTo: range?.[1]?.format('YYYY-MM-DD'),
  });
  const creatable = (opening ? (['PNK'] as InventoryDocumentType[]) : CREATABLE_TYPES).filter((type) =>
    can(`${DOCUMENT_TYPE_PERMISSION[type]}:create`),
  );
  const title = opening ? 'Tồn kho đầu kỳ' : 'Chứng từ kho';

  const create = async (values: DocumentFormValues, key: string) => {
    const created = await documentApi.create(toDocumentInput(values), key);
    message.success(`Đã lập ${created.documentNo}.`);
    navigate(`/inventory/documents/${created.id}`);
  };

  const columns: TableColumnsType<InventoryDocumentSummary> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      sorter: true,
      render: (no: string, row) => <Link to={`/inventory/documents/${row.id}`}>{no}</Link>,
    },
    {
      title: 'Loại',
      dataIndex: 'documentType',
      render: (type: InventoryDocumentType) => DOCUMENT_TYPES[type],
    },
    { title: 'Ngày', dataIndex: 'documentDate', sorter: true, width: 110, render: formatDate },
    { title: 'Kho', dataIndex: 'warehouseId', render: (id: number) => warehouses.codeOf(id) || `#${id}` },
    {
      title: 'Kho nhận',
      dataIndex: 'destinationWarehouseId',
      render: (id: number | null) => (id ? warehouses.codeOf(id) || `#${id}` : ''),
    },
    { title: 'Số dòng', dataIndex: 'lineCount', width: 80, align: 'right' },
    {
      title: 'Giá trị',
      dataIndex: 'totalAmount',
      align: 'right',
      render: (value: number | null) => formatNumber(value, 0),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: InventoryDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4}>{title}</Typography.Title>
      <ListToolbar
        searchPlaceholder="Tìm theo số chứng từ"
        onSearch={(value) => setKeyword(value || undefined)}
        createLabel={opening ? 'Lập phiếu tồn đầu kỳ' : 'Lập chứng từ'}
        onCreate={creatable.length > 0 ? () => setCreating(true) : undefined}
        extraFilters={
          <>
            {!opening && (
              <Select
                allowClear
                placeholder="Loại chứng từ"
                style={{ width: 210 }}
                value={documentType}
                onChange={setDocumentType}
                options={(Object.keys(DOCUMENT_TYPES) as InventoryDocumentType[]).map((value) => ({
                  value,
                  label: DOCUMENT_TYPES[value],
                }))}
              />
            )}
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 160 }}
              value={status}
              onChange={setStatus}
              options={DOCUMENT_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Kho"
              style={{ width: 200 }}
              value={warehouseId}
              onChange={setWarehouseId}
              options={warehouses.options}
            />
            <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={setRange} />
            {opening && creatable.length > 0 && (
              <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>
                Nhập Excel
              </Button>
            )}
          </>
        }
      />
      <Table<InventoryDocumentSummary> {...tableProps} columns={columns} scroll={{ x: 900 }} />
      <FormModal<DocumentFormValues>
        open={creating}
        title={opening ? 'Lập phiếu nhập tồn kho đầu kỳ' : 'Lập chứng từ kho'}
        width={980}
        okText="Lưu nháp"
        initialValues={{
          documentType: creatable[0],
          subType: opening ? OPENING_SUB_TYPE : undefined,
          documentDate: dayjs(),
          lines: [{}] as never,
        }}
        fieldNames={DOCUMENT_FIELDS}
        onSubmit={create}
        onClose={() => setCreating(false)}
      >
        {(form) => <DocumentFormFields form={form} editing={false} opening={opening} />}
      </FormModal>
      {opening && (
        <ImportModal<OpeningStockImportResult>
          open={importing}
          title="Nhập tồn kho đầu kỳ từ Excel"
          onDownloadTemplate={documentApi.downloadOpeningTemplate}
          onImport={(file, key) =>
            documentApi.importOpening(file, companyId, importDate.format('YYYY-MM-DD'), key)
          }
          successMessage={(result) =>
            `Đã nhập ${result.created} dòng, lập phiếu nháp ${result.documentNos.join(', ')}. Kiểm tra rồi ghi sổ.`
          }
          onDone={() => void queryClient.invalidateQueries({ queryKey: ['inventory-documents'] })}
          onClose={() => setImporting(false)}
        >
          <Form.Item
            label="Ngày chứng từ"
            extra="Mỗi kho trong file lập một phiếu nhập kho đầu kỳ ở trạng thái nháp."
            style={{ marginBottom: 0 }}
          >
            <DatePicker
              format="DD/MM/YYYY"
              allowClear={false}
              value={importDate}
              onChange={(value) => value && setImportDate(value)}
            />
          </Form.Item>
        </ImportModal>
      )}
    </Card>
  );
}
