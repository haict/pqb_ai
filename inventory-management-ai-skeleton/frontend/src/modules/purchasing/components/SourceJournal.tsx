import { useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { useCan } from '../../../shared/auth/authContext';
import { journalApi } from '../../accounting/api';
import { JournalEntryView } from '../../accounting/components/JournalEntryView';
import { useCompanyId } from '../../masterdata/lookups';

/** Bút toán đã sinh khi ghi sổ chứng từ nguồn (cần quyền xem bút toán). */
export function SourceJournal({
  sourceType,
  sourceId,
  status,
}: {
  sourceType: string;
  sourceId: number;
  status: string;
}) {
  const can = useCan();
  const companyId = useCompanyId();
  const posted = status === 'POSTED' || status === 'REVERSED';
  const journals = useQuery({
    queryKey: ['journal-entries', 'by-source', sourceType, sourceId, status],
    queryFn: () => journalApi.bySource(companyId, sourceType, sourceId),
    enabled: posted && can('journal:view'),
  });
  return (
    <>
      {(journals.data ?? []).map((journal) => (
        <div key={journal.entry.id} style={{ marginTop: 16 }}>
          <Typography.Title level={5}>Bút toán {journal.entry.entryNo}</Typography.Title>
          <JournalEntryView journal={journal} />
        </div>
      ))}
    </>
  );
}
