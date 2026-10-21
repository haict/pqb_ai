import { EditOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Descriptions, Result, Space, Spin, Table, Tabs, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { formatDateTime } from '../../../shared/format';
import { FormModal } from '../../../shared/components/FormModal';
import { useWarehouses } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { documentApi } from '../api';
import { DocumentActions } from '../components/DocumentActions';
import { DocumentFormFields } from '../components/DocumentForm';
import { DOCUMENT_FIELDS, toDocumentInput, type DocumentFormValues } from '../components/documentFormValues';
import {
  DOCUMENT_TYPES,
  RECEIPT_SUB_TYPES,
  SOURCE_TYPES,
  TRANSACTION_TYPES,
  formatDate,
  formatNumber,
} from '../labels';
import { DocumentStatusTag, QualityTag } from '../components/StatusTags';
import type { InventoryDocument, InventoryLine, InventoryTransaction } from '../types';

function editValues(document: InventoryDocument): Partial<DocumentFormValues> {
  return {
    documentType: document.documentType,
    subType: document.subType ?? undefined,
    documentDate: dayjs(document.documentDate),
    postingDate: dayjs(document.postingDate),
    warehouseId: document.warehouseId,
    destinationWarehouseId: document.destinationWarehouseId ?? undefined,
    partnerId: document.partnerId ?? undefined,
    reasonText: document.reasonText ?? undefined,
    note: document.note ?? undefined,
    issuePolicyId: document.issuePolicyId ?? undefined,
    lines: document.lines.map((line) => {
      const inbound = document.documentType === 'PNK' || line.direction === 'IN';
      return {
        productId: line.productId,
        unitId: line.unitId,
        quantity: line.quantity,
        direction: line.direction ?? undefined,
        locationId: line.locationId ?? undefined,
        destinationLocationId: line.destinationLocationId ?? undefined,
        lotId: inbound ? undefined : (line.lotId ?? undefined),
        lotNo: inbound ? (line.lotNo ?? undefined) : undefined,
        manufactureDate: inbound && line.manufactureDate ? dayjs(line.manufactureDate) : undefined,
        expiryDate: inbound && line.expiryDate ? dayjs(line.expiryDate) : undefined,
        qualityStatus: line.qualityStatus ?? undefined,
        targetQualityStatus: line.targetQualityStatus ?? undefined,
        unitCost: line.unitCost ?? undefined,
        lotOverrideReason: line.lotOverrideReason ?? undefined,
        note: line.note ?? undefined,
        serialNumbers: line.serialNumbers,
      };
    }),
  };
}

/** Chi tiết chứng từ kho: thông tin chung, dòng hàng, sổ kho đã ghi, lịch sử duyệt (trước D-61), chứng từ liên quan. */
export function InventoryDocumentDetailPage() {
  const { id } = useParams();
  const documentId = Number(id);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const warehouses = useWarehouses();
  const [editing, setEditing] = useState(false);
  const query = useQuery({
    queryKey: ['inventory-document', documentId],
    queryFn: () => documentApi.get(documentId),
    enabled: Number.isFinite(documentId),
  });
  const document = query.data;

  if (query.isLoading) {
    return <Spin />;
  }
  if (!document) {
    return <Result status="404" title="Không tìm thấy chứng từ" />;
  }

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['inventory-document', documentId] });
    void queryClient.invalidateQueries({ queryKey: ['inventory-documents'] });
  };
  const act = async (action: string, body: object | undefined, key: string) => {
    const result = await documentApi.act(documentId, action, body, key);
    if (action === 'reverse') {
      navigate(`/inventory/documents/${result.id}`);
    }
    refresh();
  };
  const save = async (values: DocumentFormValues) => {
    await documentApi.update(documentId, { ...toDocumentInput(values), version: document.version });
    message.success('Đã lưu chứng từ.');
    refresh();
  };
  const warehouseName = (warehouseId: number | null) =>
    warehouseId ? `${warehouses.codeOf(warehouseId)} - ${warehouses.nameOf(warehouseId)}` : '';

  const lineColumns = [
    { title: '#', dataIndex: 'lineNo', width: 50 },
    {
      title: 'Hàng hóa',
      render: (_: unknown, line: InventoryLine) => `${line.productCode ?? ''} - ${line.productName ?? ''}`,
    },
    {
      title: 'Số lượng',
      align: 'right' as const,
      render: (_: unknown, line: InventoryLine) =>
        `${formatNumber(line.quantity)}${line.conversionFactor !== 1 ? ` (= ${formatNumber(line.baseQuantity)})` : ''}`,
    },
    {
      title: 'Tăng/giảm',
      dataIndex: 'direction',
      render: (v: string | null) => (v === 'IN' ? 'Tăng' : v ? 'Giảm' : ''),
    },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Hạn dùng', dataIndex: 'expiryDate', render: formatDate },
    { title: 'Serial', dataIndex: 'serialNumbers', render: (serials: string[]) => serials.join(', ') },
    {
      title: 'Trạng thái CL',
      render: (_: unknown, line: InventoryLine) => (
        <Space size={0}>
          <QualityTag status={line.qualityStatus} />
          {line.targetQualityStatus && (
            <>
              → <QualityTag status={line.targetQualityStatus} />
            </>
          )}
        </Space>
      ),
    },
    {
      title: 'Đơn giá',
      dataIndex: 'unitCost',
      align: 'right' as const,
      render: (v: number | null) => formatNumber(v, 4),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'amount',
      align: 'right' as const,
      render: (v: number | null) => formatNumber(v, 0),
    },
  ];
  const ledgerColumns = [
    { title: 'Ngày HT', dataIndex: 'postingDate', width: 100, render: formatDate },
    {
      title: 'Nghiệp vụ',
      dataIndex: 'transactionType',
      render: (v: InventoryTransaction['transactionType']) => TRANSACTION_TYPES[v],
    },
    { title: 'Kho', dataIndex: 'warehouseId', render: (v: number) => warehouses.codeOf(v) || `#${v}` },
    {
      title: 'Hàng hóa',
      render: (_: unknown, row: InventoryTransaction) =>
        `${row.productCode ?? ''} - ${row.productName ?? ''}`,
    },
    { title: 'Lô', dataIndex: 'lotNo' },
    { title: 'Serial', dataIndex: 'serialNo' },
    {
      title: 'Trạng thái CL',
      dataIndex: 'qualityStatus',
      render: (v: InventoryTransaction['qualityStatus']) => <QualityTag status={v} />,
    },
    {
      title: 'Nhập / xuất',
      align: 'right' as const,
      render: (_: unknown, row: InventoryTransaction) =>
        `${row.direction === 'IN' ? '+' : '-'}${formatNumber(row.quantity)}`,
    },
  ];

  return (
    <Card>
      <Space style={{ width: '100%', justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {DOCUMENT_TYPES[document.documentType]} {document.documentNo}{' '}
          <DocumentStatusTag status={document.status} />
        </Typography.Title>
        <Space wrap>
          {document.allowedActions.includes('update') && (
            <Button onClick={() => setEditing(true)} icon={<EditOutlined />}>
              Sửa
            </Button>
          )}
          <DocumentActions actions={document.allowedActions} onAction={act} />
        </Space>
      </Space>
      <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginTop: 16 }}>
        {document.documentType === 'PNK' && document.subType && RECEIPT_SUB_TYPES[document.subType] && (
          <Descriptions.Item label="Nghiệp vụ">{RECEIPT_SUB_TYPES[document.subType]}</Descriptions.Item>
        )}
        <Descriptions.Item label="Ngày chứng từ">{formatDate(document.documentDate)}</Descriptions.Item>
        <Descriptions.Item label="Ngày hạch toán">{formatDate(document.postingDate)}</Descriptions.Item>
        <Descriptions.Item label="Kho">{warehouseName(document.warehouseId)}</Descriptions.Item>
        {document.destinationWarehouseId && (
          <Descriptions.Item label="Kho nhận">
            {warehouseName(document.destinationWarehouseId)}
          </Descriptions.Item>
        )}
        {document.transitWarehouseId && (
          <Descriptions.Item label="Kho đang vận chuyển">
            {warehouseName(document.transitWarehouseId)}
          </Descriptions.Item>
        )}
        {document.receivePostingDate && (
          <Descriptions.Item label="Ngày nhận">{formatDate(document.receivePostingDate)}</Descriptions.Item>
        )}
        {document.sourceNo && (
          <Descriptions.Item label="Chứng từ nguồn">
            {`${SOURCE_TYPES[document.sourceType ?? ''] ?? ''} ${document.sourceNo}`.trim()}
          </Descriptions.Item>
        )}
        <Descriptions.Item label="Lý do">{document.reasonText}</Descriptions.Item>
        <Descriptions.Item label="Ghi chú">{document.note}</Descriptions.Item>
        <Descriptions.Item label="Giá trị">{formatNumber(document.totalAmount, 0)}</Descriptions.Item>
        <Descriptions.Item label="Ghi sổ lúc">{formatDateTime(document.postedAt)}</Descriptions.Item>
        {document.reversalOfId && (
          <Descriptions.Item label="Đảo cho">
            <Link to={`/inventory/documents/${document.reversalOfId}`}>{document.reversalOfNo}</Link>
          </Descriptions.Item>
        )}
        {document.reversedById && (
          <Descriptions.Item label="Chứng từ đảo">
            <Link to={`/inventory/documents/${document.reversedById}`}>{document.reversedByNo}</Link>
          </Descriptions.Item>
        )}
        {document.cancelReason && (
          <Descriptions.Item label="Lý do hủy">{document.cancelReason}</Descriptions.Item>
        )}
      </Descriptions>
      <Tabs
        items={[
          {
            key: 'lines',
            label: `Dòng hàng (${document.lines.length})`,
            children: (
              <Table<InventoryLine>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={document.lines}
                columns={lineColumns}
                scroll={{ x: 1000 }}
              />
            ),
          },
          {
            key: 'ledger',
            label: `Sổ kho (${document.transactions.length})`,
            children: (
              <Table<InventoryTransaction>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={document.transactions}
                columns={ledgerColumns}
                scroll={{ x: 900 }}
              />
            ),
          },
          ...(document.approvals.length > 0
            ? [
                {
                  key: 'approvals',
                  label: 'Lịch sử duyệt',
                  children: <ApprovalHistory requests={document.approvals} />,
                },
              ]
            : []),
        ]}
      />
      <FormModal<DocumentFormValues>
        open={editing}
        title={`Sửa ${document.documentNo}`}
        width={980}
        initialValues={editValues(document)}
        fieldNames={DOCUMENT_FIELDS}
        onSubmit={save}
        onClose={() => setEditing(false)}
      >
        {(form) => <DocumentFormFields form={form} editing />}
      </FormModal>
    </Card>
  );
}
