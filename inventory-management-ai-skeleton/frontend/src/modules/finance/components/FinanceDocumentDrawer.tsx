import { EditOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Space, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useCan } from '../../../shared/auth/authContext';
import { journalApi } from '../../accounting/api';
import { JournalEntryView } from '../../accounting/components/JournalEntryView';
import { DocumentActions } from '../../inventory/components/DocumentActions';
import { DocumentStatusTag } from '../../inventory/components/StatusTags';
import { useCompanyId } from '../../masterdata/lookups';
import { ApprovalHistory } from '../../system/components/ApprovalHistory';
import { financeActions } from '../labels';
import type { FinanceDocumentBase } from '../types';

interface Props<T extends FinanceDocumentBase> {
  id: number;
  /** Khóa cache chi tiết và danh sách cần làm mới sau thao tác. */
  queryKey: string;
  listKeys: string[];
  load(id: number): Promise<T>;
  act(id: number, action: string, body: object | undefined, key: string): Promise<T>;
  /** Tiền tố quyền: {@code <prefix>:create|post|reverse}. */
  permission: string;
  title(document: T): string;
  /** Loại chứng từ nguồn của bút toán; {@code null} = không có bút toán. */
  journalSource?(document: T): string | null;
  onEdit(document: T): void;
  onClose(): void;
  children(document: T): ReactNode;
}

/**
 * Drawer chi tiết chứng từ ghi sổ công nợ, quỹ: trạng thái, nút thao tác theo trạng thái và quyền (server vẫn kiểm
 * tra), nội dung riêng, bút toán đã sinh, lịch sử duyệt (chứng từ trước D-61).
 */
export function FinanceDocumentDrawer<T extends FinanceDocumentBase>(props: Props<T>) {
  const { id, queryKey, listKeys, load, act, permission, title, journalSource, onEdit, onClose, children } =
    props;
  const can = useCan();
  const queryClient = useQueryClient();
  const companyId = useCompanyId();
  const query = useQuery({ queryKey: [queryKey, id], queryFn: () => load(id) });
  const document = query.data;
  const posted = document && (document.status === 'POSTED' || document.status === 'REVERSED');
  const source = document && journalSource ? journalSource(document) : null;
  const journals = useQuery({
    queryKey: ['journal-entries', 'by-source', source, id, document?.status],
    queryFn: () => journalApi.bySource(companyId, source ?? '', id),
    enabled: !!posted && !!source && can('journal:view'),
  });
  const editable = document?.status === 'DRAFT';
  return (
    <Drawer open width={1000} title={document ? title(document) : ''} onClose={onClose}>
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
              <DocumentActions
                actions={financeActions(document.status, permission, can)}
                onAction={async (action, body, key) => {
                  await act(id, action, body, key);
                  void queryClient.invalidateQueries({ queryKey: [queryKey, id] });
                  listKeys.forEach((key) => void queryClient.invalidateQueries({ queryKey: [key] }));
                }}
              />
            </Space>
          </Space>
          {children(document)}
          {(journals.data ?? []).map((journal) => (
            <div key={journal.entry.id} style={{ marginTop: 16 }}>
              <Typography.Title level={5}>Bút toán {journal.entry.entryNo}</Typography.Title>
              <JournalEntryView journal={journal} />
            </div>
          ))}
          <ApprovalHistory requests={document.approvals} />
        </>
      )}
    </Drawer>
  );
}
