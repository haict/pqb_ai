import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Space, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { purchaseActions } from '../labels';
import type { PurchaseDocumentBase } from '../types';

interface Props<T extends PurchaseDocumentBase> {
  id: number;
  /** Khóa cache chi tiết và danh sách cần làm mới sau thao tác. */
  queryKey: string;
  listKeys: string[];
  load(id: number): Promise<T>;
  act(id: number, action: string, body: object | undefined, key: string): Promise<T>;
  /** Tiền tố quyền: {@code <prefix>:create|approve|close}. */
  permission: string;
  title(document: T): string;
  /** Thao tác riêng của loại chứng từ; mặc định theo {@link purchaseActions}. */
  actions?(document: T): string[];
  actionOrder?: string[];
  onEdit(document: T): void;
  onClose(): void;
  children(document: T): ReactNode;
}

/**
 * Drawer chi tiết chứng từ mua hàng: trạng thái, nút thao tác theo trạng thái và quyền (server vẫn kiểm tra), nội dung
 * riêng, lịch sử duyệt.
 */
export function PurchaseDocumentDrawer<T extends PurchaseDocumentBase>(props: Props<T>) {
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
                <Button onClick={() => onEdit(document)}>Sửa</Button>
              )}
              <DocumentActions
                actions={actions ? actions(document) : purchaseActions(document.status, permission, can)}
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
