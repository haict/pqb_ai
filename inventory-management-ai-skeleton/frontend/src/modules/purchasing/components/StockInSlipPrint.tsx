import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Spin } from 'antd';
import { toApiError } from '../../../shared/api/apiError';
import { InventorySlipLayout, type InventorySlipView } from '../../../shared/components/InventorySlipLayout';
import { SlipDownloadButton } from '../../../shared/inventorySlip/SlipDownloadButton';
import { formatDate } from '../../inventory/labels';
import { useCatalog } from '../../masterdata/lookups';
import { purchaseOrderApi } from '../api';
import type { StockInSlip } from '../types';

/** Phiếu nhập kho in từ đơn mua đã ghi sổ (BR-PUR-08): dữ liệu do server tổng hợp, chỉ dòng hàng hóa. */
export function StockInSlipPrint({ orderId, companyId }: { orderId: number; companyId?: number }) {
  const units = useCatalog('units');
  const slip = useQuery({
    queryKey: ['purchase-orders', orderId, 'stock-in-slip', companyId],
    queryFn: () => purchaseOrderApi.stockInSlip(orderId, companyId),
  });
  if (slip.isPending) {
    return <Spin />;
  }
  if (slip.isError) {
    return <Alert type="error" showIcon message={toApiError(slip.error).message} />;
  }
  return <InventorySlipLayout slip={toView(slip.data, units.nameOf)} />;
}

/** Tải phiếu nhập kho về máy (Excel, Word, PDF) theo công ty đang chọn, đặt ngoài hộp xem trước. */
export function StockInSlipDownload({ orderId, companyId }: { orderId: number; companyId?: number }) {
  const units = useCatalog('units');
  const queryClient = useQueryClient();
  return (
    <SlipDownloadButton
      load={async () =>
        toView(
          await queryClient.fetchQuery({
            queryKey: ['purchase-orders', orderId, 'stock-in-slip', companyId],
            queryFn: () => purchaseOrderApi.stockInSlip(orderId, companyId),
          }),
          units.nameOf,
        )
      }
    />
  );
}

function toView(slip: StockInSlip, unitName: (id: number | null) => string): InventorySlipView {
  const invoice = slip.invoiceNo
    ? `${slip.invoiceNo} ngày ${formatDate(slip.invoiceDate)} của ${slip.supplierName ?? ''}`
    : '';
  return {
    title: 'PHIẾU NHẬP KHO',
    companyName: slip.companyName,
    companyAddress: slip.companyAddress,
    slipDate: slip.slipDate,
    slipNo: slip.slipNo,
    debitAccounts: slip.debitAccounts,
    creditAccounts: slip.creditAccounts,
    infoRows: [
      `Họ và tên người giao hàng: ${slip.supplierName ?? ''}`,
      `Địa chỉ: ${slip.supplierAddress ?? ''}`,
      `Mã số thuế: ${slip.supplierTaxCode ?? ''}`,
      `Diễn giải: ${slip.description}`,
      `Theo hóa đơn số ${invoice}`,
      `Nhập tại kho: ${slip.warehouseName ?? ''}`,
    ],
    lines: slip.lines.map((line) => ({
      lineNo: line.lineNo,
      productCode: line.productCode,
      productName: line.productName,
      lots: line.lotNo ? [line.lotNo] : [],
      expiries: line.expiryDate ? [formatDate(line.expiryDate)] : [],
      warehouseCode: slip.warehouseCode,
      unitName: unitName(line.unitId),
      quantity: line.quantity,
      unitPrice: line.unitPrice,
      amount: line.amount,
    })),
    totalAmount: slip.totalAmount,
    taxRates: slip.taxRates,
    taxAmount: slip.taxAmount,
    paymentAmount: slip.paymentAmount,
    signers: ['Người lập phiếu', 'Người giao hàng', 'Thủ kho', 'Kế toán trưởng'],
  };
}
