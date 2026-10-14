import { useQuery } from '@tanstack/react-query';
import { Alert, Spin } from 'antd';
import { toApiError } from '../../../shared/api/apiError';
import { InventorySlipLayout, type InventorySlipView } from '../../../shared/components/InventorySlipLayout';
import { SlipDownloadButton } from '../../../shared/inventorySlip/SlipDownloadButton';
import { formatDate } from '../../inventory/labels';
import { useCatalog } from '../../masterdata/lookups';
import { purchaseOrderApi } from '../api';
import type { StockInSlip } from '../types';

/** Phiếu nhập kho in từ đơn mua đã ghi sổ (BR-PUR-08): dữ liệu do server tổng hợp, chỉ dòng hàng hóa. */
export function StockInSlipPrint({ orderId }: { orderId: number }) {
  const units = useCatalog('units');
  const slip = useQuery({
    queryKey: ['purchase-orders', orderId, 'stock-in-slip'],
    queryFn: () => purchaseOrderApi.stockInSlip(orderId),
  });
  if (slip.isPending) {
    return <Spin />;
  }
  if (slip.isError) {
    return <Alert type="error" showIcon message={toApiError(slip.error).message} />;
  }
  const view = toView(slip.data, units.nameOf);
  return (
    <>
      <div className="no-print" style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <SlipDownloadButton slip={view} />
      </div>
      <InventorySlipLayout slip={view} />
    </>
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
