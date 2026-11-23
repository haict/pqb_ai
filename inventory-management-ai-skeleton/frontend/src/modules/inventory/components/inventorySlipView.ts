import type { InventorySlipView } from '../../../shared/inventorySlip/slipContent';
import { formatDate, formatNumber } from '../labels';
import type { InventorySlip, InventorySlipLine } from '../types';

/** Lô thực nhập / xuất của dòng: một lô chỉ ghi số lô; nhiều lô kèm số lượng (đơn vị cơ sở) từng lô. */
function lotRows(line: InventorySlipLine) {
  const lots = new Map<string, { expiry: string | null; quantity: number }>();
  line.items
    .filter((item) => item.lotNo)
    .forEach((item) => {
      const lot = item.lotNo as string;
      lots.set(lot, {
        expiry: item.expiryDate,
        quantity: (lots.get(lot)?.quantity ?? 0) + item.baseQuantity,
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

type SlipHeader = Pick<InventorySlipView, 'title' | 'infoRows' | 'signers'>;

function partnerRows(slip: InventorySlip, role: string) {
  return [
    `Họ và tên ${role}: ${slip.partnerName ?? ''}`,
    `Địa chỉ: ${slip.partnerAddress ?? ''}`,
    `Mã số thuế: ${slip.partnerTaxCode ?? ''}`,
    `Diễn giải: ${slip.description}`,
  ];
}

/** Đầu phiếu theo loại: nhập kho, chuyển kho (thêm kho nhận; người nhận hàng là khách của phiếu), còn lại xuất kho. */
function header(slip: InventorySlip): SlipHeader {
  if (slip.documentType === 'PCK') {
    return {
      title: 'PHIẾU CHUYỂN KHO',
      infoRows: [
        ...partnerRows(slip, 'người nhận hàng'),
        `Xuất tại kho: ${slip.warehouseName ?? ''}`,
        `Nhập tại kho: ${slip.destinationWarehouseName ?? ''}`,
      ],
      signers: ['Người lập phiếu', 'Thủ kho xuất', 'Người nhận hàng', 'Thủ kho nhập'],
    };
  }
  const inbound = slip.documentType === 'PNK';
  return {
    title: inbound ? 'PHIẾU NHẬP KHO' : 'PHIẾU XUẤT KHO',
    infoRows: [
      ...partnerRows(slip, inbound ? 'người giao hàng' : 'người nhận hàng'),
      `Theo chứng từ số: ${slip.sourceNo ?? ''}`,
      `${inbound ? 'Nhập' : 'Xuất'} tại kho: ${slip.warehouseName ?? ''}`,
    ],
    signers: ['Người lập phiếu', inbound ? 'Người giao hàng' : 'Người nhận hàng', 'Thủ kho', 'Kế toán trưởng'],
  };
}

export function toSlipView(slip: InventorySlip, unitName: (id: number | null) => string): InventorySlipView {
  return {
    ...header(slip),
    companyName: slip.companyName,
    companyAddress: slip.companyAddress,
    slipDate: slip.slipDate,
    slipNo: slip.documentNo,
    debitAccounts: [],
    creditAccounts: [],
    lines: slip.lines.map((line) => {
      const serials = line.items.map((item) => item.serialNo).filter(Boolean);
      return {
        lineNo: line.lineNo,
        productCode: line.productCode,
        productName: line.productName,
        productNote: serials.length > 0 ? `Serial: ${serials.join(', ')}` : undefined,
        ...lotRows(line),
        warehouseCode: line.warehouseCode ?? slip.warehouseCode,
        unitName: unitName(line.unitId),
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        amount: line.amount,
      };
    }),
    totalAmount: slip.totalAmount,
    taxRates: [],
    taxAmount: 0,
    paymentAmount: slip.totalAmount,
  };
}
