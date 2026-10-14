import { EyeOutlined } from '@ant-design/icons';
import { Button, Card, Drawer, Space, Table, Typography, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { documentLink } from '../../../app/documentLinks';
import { useServerTable } from '../../../shared/api/useServerTable';
import { formatDateTime } from '../../../shared/format';
import { approvalApi } from '../api';
import { ApprovalHistory } from '../components/ApprovalHistory';
import { useDocumentTypes, useMyBranches } from '../lookups';
import type { ApprovalRequest } from '../types';

const NO_FILTERS = {};

/** Việc chờ tôi duyệt (BR-SYS-11). Duyệt / từ chối thực hiện trên trang chứng từ. */
export function ApprovalsPage() {
  const documentTypes = useDocumentTypes();
  const branches = useMyBranches();
  const [viewing, setViewing] = useState<ApprovalRequest | null>(null);
  const { tableProps } = useServerTable('approvals-pending', approvalApi.pending, NO_FILTERS);

  const columns: TableColumnsType<ApprovalRequest> = [
    {
      title: 'Loại chứng từ',
      dataIndex: 'documentType',
      render: (code: string) => documentTypes.nameOf(code),
    },
    {
      title: 'Số chứng từ',
      width: 180,
      render: (_, request) => {
        const link = documentLink(request.documentType, request.documentId);
        const label = request.documentNo ?? `#${request.documentId}`;
        return link ? <Link to={link}>{label}</Link> : label;
      },
    },
    { title: 'Chi nhánh', dataIndex: 'branchId', width: 200, render: (id: number) => branches.nameOf(id) },
    {
      title: 'Giá trị',
      dataIndex: 'amount',
      width: 150,
      align: 'right',
      render: (value: number | null) => (value === null ? '' : Number(value).toLocaleString('vi-VN')),
    },
    {
      title: 'Bước',
      width: 80,
      render: (_, request) => `${request.currentStepPosition}/${request.totalSteps}`,
    },
    { title: 'Người gửi', dataIndex: 'requestedByName', width: 180 },
    { title: 'Gửi lúc', dataIndex: 'requestedAt', width: 150, render: formatDateTime },
    {
      title: 'Thao tác',
      width: 110,
      render: (_, request) => (
        <Button size="small" onClick={() => setViewing(request)} icon={<EyeOutlined />}>
          Chi tiết
        </Button>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        Việc chờ duyệt
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        Chứng từ đang chờ bạn duyệt, kể cả phần duyệt thay theo ủy quyền. Mở chứng từ để duyệt hoặc từ chối.
      </Typography.Paragraph>
      <Table<ApprovalRequest> {...tableProps} columns={columns} scroll={{ x: 1100 }} />
      <Drawer
        open={viewing !== null}
        width={640}
        title={viewing ? `${documentTypes.nameOf(viewing.documentType)} ${viewing.documentNo ?? ''}` : ''}
        onClose={() => setViewing(null)}
      >
        {viewing && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <ApprovalHistory requests={[viewing]} />
          </Space>
        )}
      </Drawer>
    </Card>
  );
}
