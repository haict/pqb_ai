import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Spin } from 'antd';
import { toApiError } from '../../../shared/api/apiError';
import { InventorySlipLayout } from '../../../shared/components/InventorySlipLayout';
import { SlipDownloadButton } from '../../../shared/inventorySlip/SlipDownloadButton';
import { useCatalog } from '../../masterdata/lookups';
import { documentApi } from '../api';
import { toSlipView } from './inventorySlipView';

const slipQuery = (documentId: number, companyId?: number) => ({
  queryKey: ['inventory-documents', documentId, 'slip', companyId],
  queryFn: () => documentApi.slip(documentId, companyId),
});

/** Phiếu nhập kho / phiếu xuất kho của chứng từ kho đã ghi sổ (D-78): dữ liệu do server tổng hợp từ dòng và sổ kho. */
export function InventorySlipPrint({ documentId, companyId }: { documentId: number; companyId?: number }) {
  const units = useCatalog('units');
  const slip = useQuery(slipQuery(documentId, companyId));
  if (slip.isPending) {
    return <Spin />;
  }
  if (slip.isError) {
    return <Alert type="error" showIcon message={toApiError(slip.error).message} />;
  }
  return <InventorySlipLayout slip={toSlipView(slip.data, units.nameOf)} />;
}

/** Tải phiếu về máy (Excel, Word, PDF) theo công ty đang chọn. */
export function InventorySlipDownload({ documentId, companyId }: { documentId: number; companyId?: number }) {
  const units = useCatalog('units');
  const queryClient = useQueryClient();
  return (
    <SlipDownloadButton
      load={async () =>
        toSlipView(await queryClient.fetchQuery(slipQuery(documentId, companyId)), units.nameOf)
      }
    />
  );
}
