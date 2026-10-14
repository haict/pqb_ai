import { EditOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Space, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import type { SalesDocumentBase } from '../types';

interface Props<T extends SalesDocumentBase> {
  id: number;
  /** Khóa cache chi tiết và danh sách cần làm mới sau thao tác. */
  queryKey: string;
  listKeys: string[];
  load(id: number): Promise<T>;
  act(id: number, action: string, body: object | undefined, key: string): Promise<T>;
  /** Tiền tố quyền: {@code <prefix>:create} (nút sửa). */
  permission: string;
  title(document: T): string;
  /** Thao tác theo trạng thái và quyền của loại chứng từ. */
  actions(document: T): string[];
  actionOrder?: string[];
  onEdit(document: T): void;
  onClose(): void;
  /** Nút riêng cạnh nút thao tác (in, khách nhận hàng...). */
  extra?(document: T): ReactNode;
  children(document: T): ReactNode;
}

/**
 * Drawer chi tiết chứng từ bán hàng: trạng thái, nút thao tác theo trạng thái và quyền (server vẫn kiểm tra), nội dung
 * riêng, lịch sử duyệt.
 */
export function SalesDocumentDrawer<T extends SalesDocumentBase>(props: Props<T>) {
  const {
    id,
    queryKey,
    listKeys,
    load,
    act,
    permission,
    title,
    actions,
    actionOrder,
    onEdit,
    onClose,
    extra,
    children,
  } = props;
  const can = useCan();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: [queryKey, id], queryFn: () => load(id) });
  const document = query.data;
  const editable = document && (document.status === 'DRAFT' || document.status === 'REJECTED');
  return (
    <Drawer open width={1100} title={document ? title(document) : ''} onClose={onClose}>
      {document && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={document.status} />
            <Space wrap>
              {editable && can(`${permission}:create`) && (
                <Button onClick={() => onEdit(document)} icon={<EditOutlined />}>
                  Sửa
                </Button>
              )}
              {extra?.(document)}
              <DocumentActions
                actions={actions(document)}
                order={actionOrder}
                onAction={async (action, body, key) => {
                  await act(id, action, body, key);
                  void queryClient.invalidateQueries({ queryKey: [queryKey, id] });
                  listKeys.forEach((key) => void queryClient.invalidateQueries({ queryKey: [key] }));
                }}
              />
            </Space>
          </Space>
          {children(document)}
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            Lịch sử duyệt
          </Typography.Title>
          <ApprovalHistory requests={document.approvals} />
        </>
      )}
    </Drawer>
  );
}
