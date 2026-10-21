import { EditOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Space } from 'antd';
import type { ReactNode } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { postingActions } from '../labels';
import type { PurchaseDocumentBase } from '../types';

interface Props<T extends PurchaseDocumentBase> {
  id: number;
  /** Khóa cache chi tiết và danh sách cần làm mới sau thao tác. */
  queryKey: string;
  listKeys: string[];
  load(id: number): Promise<T>;
  act(id: number, action: string, body: object | undefined, key: string): Promise<T>;
  /** Tiền tố quyền: {@code <prefix>:create|post|reverse}. */
  permission: string;
  title(document: T): string;
  /** Thao tác riêng của loại chứng từ; mặc định theo {@link postingActions}. */
  actions?(document: T): string[];
  actionOrder?: string[];
  onEdit(document: T): void;
  /** Nút riêng của loại chứng từ (ví dụ in), đặt trước nút thao tác. */
  extra?(document: T): ReactNode;
  onClose(): void;
  children(document: T): ReactNode;
}

/**
 * Drawer chi tiết chứng từ mua hàng: trạng thái, nút thao tác theo trạng thái và quyền (server vẫn kiểm tra), nội dung
 * riêng, lịch sử duyệt (chứng từ trước D-61).
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
    extra,
  } = props;
  const can = useCan();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: [queryKey, id], queryFn: () => load(id) });
  const document = query.data;
  const editable = document?.status === 'DRAFT';
  return (
    <Drawer open width={1100} title={document ? title(document) : ''} onClose={onClose}>
      {document && (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <DocumentStatusTag status={document.status} />
            <Space wrap>
              {extra?.(document)}
              {editable && can(`${permission}:create`) && (
                <Button onClick={() => onEdit(document)} icon={<EditOutlined />}>
                  Sửa
                </Button>
              )}
              <DocumentActions
                actions={actions ? actions(document) : postingActions(document.status, permission, can)}
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
          <ApprovalHistory requests={document.approvals} />
        </>
      )}
    </Drawer>
  );
}
