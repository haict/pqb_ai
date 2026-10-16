import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Spin } from 'antd';
import { toApiError } from '../../../shared/api/apiError';
import { InventorySlipLayout, type InventorySlipView } from '../../../shared/components/InventorySlipLayout';
import { SlipDownloadButton } from '../../../shared/inventorySlip/SlipDownloadButton';
import { formatDate, formatNumber } from '../../inventory/labels';
import { useCatalog } from '../../masterdata/lookups';
import { salesOrderApi } from '../api';
import type { StockOutSlip, StockOutSlipLine } from '../types';

/** Phiếu xuất kho in từ đơn bán đã ghi sổ: dữ liệu do server tổng hợp, chỉ dòng hàng hóa, lô / serial thực xuất. */
export function StockOutSlipPrint({ orderId, companyId }: { orderId: number; companyId?: number }) {
  const units = useCatalog('units');
  const slip = useQuery({
    queryKey: ['sales-orders', orderId, 'stock-out-slip', companyId],
    queryFn: () => salesOrderApi.stockOutSlip(orderId, companyId),
  });
  if (slip.isPending) {
    return <Spin />;
  }
  if (slip.isError) {
    return <Alert type="error" showIcon message={toApiError(slip.error).message} />;
  }
  return <InventorySlipLayout slip={toView(slip.data, units.nameOf)} />;
}

/** Tải phiếu xuất kho về máy (Excel, Word, PDF) theo công ty đang chọn, đặt ngoài hộp xem trước. */
export function StockOutSlipDownload({ orderId, companyId }: { orderId: number; companyId?: number }) {
  const units = useCatalog('units');
  const queryClient = useQueryClient();
  return (
    <SlipDownloadButton
      load={async () =>
        toView(
          await queryClient.fetchQuery({
            queryKey: ['sales-orders', orderId, 'stock-out-slip', companyId],
            queryFn: () => salesOrderApi.stockOutSlip(orderId, companyId),
          }),
          units.nameOf,
        )
      }
    />
  );
}

/** Lô thực xuất của dòng: một lô thì chỉ ghi số lô; nhiều lô thì kèm số lượng (đơn vị cơ sở) từng lô. */
function lotRows(line: StockOutSlipLine) {
  const lots = new Map<string, { expiry: string | null; quantity: number }>();
  line.items
    .filter((item) => item.lotNo)
    .forEach((item) => {
      const lot = item.lotNo as string;
      const current = lots.get(lot);
      lots.set(lot, {
        expiry: item.expiryDate,
        quantity: (current?.quantity ?? 0) + item.baseQuantity,
      });
    });
  const entries = [...lots.entries()];
  return {
    lots: entries.map(([lot, value]) =>
      entries.length > 1 ? `${lot} (${formatNumber(value.quantity)})` : lot,
    ),
    expiries: entries.map(([, value]) => formatDate(value.expiry)),
  };
}

function toView(slip: StockOutSlip, unitName: (id: number | null) => string): InventorySlipView {
  const receiver = [slip.customerName, slip.receiverName].filter(Boolean).join(' - ');
  const einvoice = slip.einvoiceNo
    ? `${slip.einvoiceSeries ?? ''}-${slip.einvoiceNo} ngày ${formatDate(slip.einvoiceDate)}`
    : '';
  return {
    title: 'PHIẾU XUẤT KHO',
    companyName: slip.companyName,
    companyAddress: slip.companyAddress,
    slipDate: slip.slipDate,
    slipNo: slip.slipNo,
    debitAccounts: slip.debitAccounts,
    creditAccounts: slip.creditAccounts,
    infoRows: [
      `Họ và tên người nhận hàng: ${receiver}`,
      `Địa chỉ: ${slip.customerAddress ?? ''}`,
      `Mã số thuế: ${slip.customerTaxCode ?? ''}`,
      `Diễn giải: ${slip.description}`,
      `Theo đơn bán số ${slip.orderNo} ngày ${formatDate(slip.orderDate)}; hóa đơn số ${einvoice}`,
      `Xuất tại kho: ${slip.warehouseName ?? ''}`,
    ],
    lines: slip.lines.map((line) => {
      const serials = line.items.map((item) => item.serialNo).filter(Boolean);
      return {
        lineNo: line.lineNo,
        productCode: line.productCode,
        productName: line.productName,
        productNote: serials.length > 0 ? `Serial: ${serials.join(', ')}` : undefined,
        ...lotRows(line),
        warehouseCode: slip.warehouseCode,
        unitName: unitName(line.unitId),
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        amount: line.amount,
      };
    }),
    totalAmount: slip.totalAmount,
    taxRates: slip.taxRates,
    taxAmount: slip.taxAmount,
    paymentAmount: slip.paymentAmount,
    signers: ['Người lập phiếu', 'Người nhận hàng', 'Thủ kho', 'Kế toán trưởng'],
  };
}
