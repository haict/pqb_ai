import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Table,
  Typography,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useServerTable } from '../../../shared/api/useServerTable';
import { useCan } from '../../../shared/auth/authContext';
import { FormModal } from '../../../shared/components/FormModal';
import { ListToolbar } from '../../../shared/components/ListToolbar';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { formatDate, formatNumber, statusLabel } from '../../inventory/labels';
import { PartnerSelect, ProductSelect } from '../../masterdata/components/EntitySelects';
import { useCatalog, useCompanyId } from '../../masterdata/lookups';
import { useMyBranches } from '../../system/lookups';
import { requisitionApi } from '../api';
import { PurchaseDocumentDrawer } from '../components/PurchaseDocumentDrawer';
import { useProductUnits } from '../components/productUnits';
import { PURCHASE_STATUSES } from '../labels';
import type { PurchaseDocumentStatus, PurchaseRequisition, PurchaseRequisitionLine } from '../types';

interface LineForm {
  productId?: number;
  productLabel?: string;
  unitId?: number;
  quantity?: number;
  suggestedSupplierId?: number;
  supplierLabel?: string;
  requiredDate?: Dayjs;
  note?: string;
}

interface RequisitionForm {
  branchId: number;
  documentDate: Dayjs;
  requiredDate?: Dayjs;
  description?: string;
  lines: LineForm[];
}

const FIELDS = ['branchId', 'documentDate', 'requiredDate', 'description', 'lines'];

function lineColumns(unitName: (id: number | null) => string): TableColumnsType<PurchaseRequisitionLine> {
  return [
    { title: '#', dataIndex: 'lineNo', width: 50 },
    { title: 'Hàng hóa', render: (_, row) => `${row.productCode ?? ''} - ${row.productName ?? ''}` },
    {
      title: 'Số lượng',
      width: 130,
      align: 'right',
      render: (_, row) => `${formatNumber(row.quantity)} ${unitName(row.unitId)}`,
    },
    {
      title: 'Quy đổi',
      width: 120,
      align: 'right',
      render: (_, row) => `${formatNumber(row.baseQuantity)} ${unitName(row.baseUnitId)}`,
    },
    {
      title: 'Đã đặt',
      dataIndex: 'orderedQuantity',
      width: 100,
      align: 'right',
      render: (v) => formatNumber(v),
    },
    {
      title: 'Đã đóng',
      dataIndex: 'closedQuantity',
      width: 100,
      align: 'right',
      render: (v) => formatNumber(v),
    },
    {
      title: 'Còn lại',
      dataIndex: 'remainingQuantity',
      width: 100,
      align: 'right',
      render: (v) => formatNumber(v),
    },
    { title: 'NCC gợi ý', dataIndex: 'suggestedSupplierName', width: 180 },
    { title: 'Ngày cần', dataIndex: 'requiredDate', width: 110, render: formatDate },
    { title: 'Ghi chú', dataIndex: 'note' },
  ];
}

/** Đề nghị mua hàng (DNM, BR-PUR-01): lập tay, duyệt theo luồng, theo dõi số đã đặt, đóng phần còn lại. */
export function PurchaseRequisitionsPage() {
  const can = useCan();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const branches = useMyBranches();
  const units = useCatalog('units');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<PurchaseDocumentStatus>();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<PurchaseRequisition | 'new' | null>(null);
  const { tableProps } = useServerTable('purchase-requisitions', requisitionApi.list, {
    companyId,
    keyword,
    status,
  });

  const save = async (values: RequisitionForm, key: string) => {
    const input = {
      branchId: values.branchId,
      documentDate: values.documentDate.format('YYYY-MM-DD'),
      requiredDate: values.requiredDate ? values.requiredDate.format('YYYY-MM-DD') : null,
      description: values.description,
      lines: (values.lines ?? []).map((line) => ({
        productId: line.productId,
        unitId: line.unitId,
        quantity: line.quantity,
        suggestedSupplierId: line.suggestedSupplierId ?? null,
        requiredDate: line.requiredDate ? line.requiredDate.format('YYYY-MM-DD') : null,
        note: line.note,
      })),
    };
    if (editing === 'new') {
      const created = await requisitionApi.create(input, key);
      message.success(`Đã lập ${created.documentNo}.`);
      setSelected(created.id);
    } else if (editing) {
      await requisitionApi.update(editing.id, { ...input, version: editing.version });
      message.success('Đã lưu đề nghị mua.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-requisition', editing.id] });
    }
    await queryClient.invalidateQueries({ queryKey: ['purchase-requisitions'] });
  };

  const initialValues: Partial<RequisitionForm> =
    editing && editing !== 'new'
      ? {
          branchId: editing.branchId,
          documentDate: dayjs(editing.documentDate),
          requiredDate: editing.requiredDate ? dayjs(editing.requiredDate) : undefined,
          description: editing.description ?? undefined,
          lines: editing.lines.map((line) => ({
            productId: line.productId,
            productLabel: `${line.productCode ?? ''} - ${line.productName ?? ''}`,
            unitId: line.unitId,
            quantity: line.quantity,
            suggestedSupplierId: line.suggestedSupplierId ?? undefined,
            supplierLabel: line.suggestedSupplierName ?? undefined,
            requiredDate: line.requiredDate ? dayjs(line.requiredDate) : undefined,
            note: line.note ?? undefined,
          })),
        }
      : { branchId: branches.items[0]?.id, documentDate: dayjs(), lines: [{}] };

  const columns: TableColumnsType<PurchaseRequisition> = [
    {
      title: 'Số chứng từ',
      dataIndex: 'documentNo',
      width: 190,
      sorter: true,
      render: (value: string, row) => <a onClick={() => setSelected(row.id)}>{value}</a>,
    },
    { title: 'Ngày', dataIndex: 'documentDate', width: 110, sorter: true, render: formatDate },
    { title: 'Ngày cần', dataIndex: 'requiredDate', width: 110, render: formatDate },
    { title: 'Chi nhánh', dataIndex: 'branchId', width: 160, render: (id: number) => branches.nameOf(id) },
    { title: 'Người đề nghị', dataIndex: 'requesterName', width: 180 },
    { title: 'Diễn giải', dataIndex: 'description' },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: PurchaseDocumentStatus) => <DocumentStatusTag status={value} />,
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Đề nghị mua hàng
      </Typography.Title>
      <ListToolbar
        searchPlaceholder="Số chứng từ, diễn giải"
        onSearch={setKeyword}
        extraFilters={
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 160 }}
            value={status}
            onChange={setStatus}
            options={PURCHASE_STATUSES.map((value) => ({ value, label: statusLabel(value) }))}
          />
        }
        createLabel="Lập đề nghị"
        onCreate={can('purchase-requisition:create') ? () => setEditing('new') : undefined}
      />
      <Table<PurchaseRequisition> {...tableProps} size="small" columns={columns} scroll={{ x: 1100 }} />
      {selected !== null && (
        <PurchaseDocumentDrawer<PurchaseRequisition>
          id={selected}
          queryKey="purchase-requisition"
          listKeys={['purchase-requisitions']}
          load={requisitionApi.get}
          act={requisitionApi.act}
          permission="purchase-requisition"
          title={(document) => `Đề nghị mua ${document.documentNo}`}
          onEdit={(document) => setEditing(document)}
          onClose={() => setSelected(null)}
        >
          {(document) => (
            <>
              <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Chi nhánh">{branches.nameOf(document.branchId)}</Descriptions.Item>
                <Descriptions.Item label="Người đề nghị">{document.requesterName}</Descriptions.Item>
                <Descriptions.Item label="Ngày">{formatDate(document.documentDate)}</Descriptions.Item>
                <Descriptions.Item label="Ngày cần">{formatDate(document.requiredDate)}</Descriptions.Item>
                {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
                <Descriptions.Item label="Diễn giải" span={2}>
                  {document.description}
                </Descriptions.Item>
              </Descriptions>
              <Table<PurchaseRequisitionLine>
                rowKey="id"
                size="small"
                pagination={false}
                columns={lineColumns(units.nameOf)}
                dataSource={document.lines}
                scroll={{ x: 1200 }}
              />
            </>
          )}
        </PurchaseDocumentDrawer>
      )}
      <FormModal<RequisitionForm>
        open={editing !== null}
        title={editing === 'new' ? 'Lập đề nghị mua' : 'Sửa đề nghị mua'}
        width={1100}
        okText={editing === 'new' ? 'Lưu nháp' : 'Lưu'}
        initialValues={initialValues}
        fieldNames={FIELDS}
        onSubmit={save}
        onClose={() => setEditing(null)}
      >
        {(form) => (
          <RequisitionFields form={form} isNew={editing === 'new'} branchOptions={branches.options} />
        )}
      </FormModal>
    </Card>
  );
}

function RequisitionFields({
  form,
  isNew,
  branchOptions,
}: {
  form: FormInstance<RequisitionForm>;
  isNew: boolean;
  branchOptions: { value: number; label: string }[];
}) {
  return (
    <>
      <Row gutter={12}>
        <Col xs={24} md={8}>
          <Form.Item
            name="branchId"
            label="Chi nhánh"
            rules={[{ required: true, message: 'Chọn chi nhánh' }]}
          >
            <Select disabled={!isNew} options={branchOptions} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="documentDate" label="Ngày" rules={[{ required: true, message: 'Chọn ngày' }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={12} md={4}>
          <Form.Item name="requiredDate" label="Ngày cần hàng">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="description" label="Diễn giải">
            <Input maxLength={500} />
          </Form.Item>
        </Col>
      </Row>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <>
            {fields.map((field) => (
              <RequisitionLineFields
                key={field.key}
                form={form}
                name={field.name}
                removable={fields.length > 1}
                onRemove={() => remove(field.name)}
              />
            ))}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              Thêm dòng
            </Button>
          </>
        )}
      </Form.List>
    </>
  );
}

function RequisitionLineFields({
  form,
  name,
  removable,
  onRemove,
}: {
  form: FormInstance<RequisitionForm>;
  name: number;
  removable: boolean;
  onRemove(): void;
}) {
  const productId = Form.useWatch(['lines', name, 'productId'], form) as number | undefined;
  const { options } = useProductUnits(productId);
  return (
    <Row gutter={8}>
      <Col xs={24} md={7}>
        <Form.Item
          name={[name, 'productId']}
          label="Hàng hóa"
          rules={[{ required: true, message: 'Chọn hàng' }]}
        >
          <ProductSelect
            selectedLabel={form.getFieldValue(['lines', name, 'productLabel'])}
            onProduct={(product) =>
              product && form.setFieldValue(['lines', name, 'unitId'], product.baseUnitId)
            }
          />
        </Form.Item>
      </Col>
      <Col xs={12} md={3}>
        <Form.Item name={[name, 'unitId']} label="Đơn vị" rules={[{ required: true, message: 'Chọn ĐVT' }]}>
          <Select options={options} />
        </Form.Item>
      </Col>
      <Col xs={12} md={3}>
        <Form.Item
          name={[name, 'quantity']}
          label="Số lượng"
          rules={[{ required: true, message: 'Nhập số lượng' }]}
        >
          <InputNumber min={0.001} precision={3} style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col xs={24} md={5}>
        <Form.Item name={[name, 'suggestedSupplierId']} label="NCC gợi ý">
          <PartnerSelect
            allowClear
            role="SUPPLIER"
            selectedLabel={form.getFieldValue(['lines', name, 'supplierLabel'])}
          />
        </Form.Item>
      </Col>
      <Col xs={12} md={2}>
        <Form.Item name={[name, 'requiredDate']} label="Ngày cần">
          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>
      </Col>
      <Col xs={10} md={3}>
        <Form.Item name={[name, 'note']} label="Ghi chú">
          <Input maxLength={500} />
        </Form.Item>
      </Col>
      <Col xs={2} md={1}>
        <Button
          style={{ marginTop: 30 }}
          icon={<DeleteOutlined />}
          disabled={!removable}
          onClick={onRemove}
        />
      </Col>
    </Row>
  );
}
