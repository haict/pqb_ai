import { UploadOutlined } from '@ant-design/icons';
import { Button, Form, type FormInstance } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { ImportModal } from '../../masterdata/components/ImportModal';
import { useCompanyId } from '../../masterdata/lookups';
import { salesOrderApi } from '../api';
import type { ImportedSalesLine, SalesLineImportResult } from '../types';
import type { SalesLineForm } from './salesLines';

/**
 * Nút "Nhập dòng từ Excel" trong form đơn bán: server chỉ đọc file và trả dòng, dòng được nối sau các dòng đã chọn
 * hàng (dòng trống bị bỏ). Không lưu đơn; người dùng kiểm tra rồi bấm Lưu như lập tay.
 */
export function SalesLineImportButton<T extends { lines?: SalesLineForm[]; documentDate?: Dayjs }>({
  form,
}: {
  form: FormInstance<T>;
}) {
  const companyId = useCompanyId();
  const [open, setOpen] = useState(false);
  const typed = form as unknown as FormInstance<{ lines?: SalesLineForm[]; documentDate?: Dayjs }>;
  const documentDate = Form.useWatch('documentDate', typed);

  const fillLines = (result: SalesLineImportResult) => {
    const kept = (typed.getFieldValue('lines') ?? []).filter((line: SalesLineForm) => line.productId);
    typed.setFieldValue('lines', [...kept, ...result.lines.map(toLineForm)]);
  };

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', margin: '8px 0' }}>
        <Button icon={<UploadOutlined />} onClick={() => setOpen(true)}>
          Nhập dòng từ Excel
        </Button>
      </div>
      <ImportModal<SalesLineImportResult>
        open={open}
        title="Nhập dòng hàng đơn bán từ Excel"
        onDownloadTemplate={salesOrderApi.downloadLineTemplate}
        onImport={(file, key) =>
          salesOrderApi.importLines(file, companyId, documentDate?.format('YYYY-MM-DD'), key)
        }
        successMessage={(result) =>
          `Đã điền ${result.created} dòng vào đơn. Giá, lô để trống sẽ lấy theo bảng giá / FEFO khi lưu, ghi sổ.`
        }
        onDone={fillLines}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

function toLineForm(line: ImportedSalesLine): SalesLineForm {
  return {
    productId: line.productId,
    productLabel: `${line.productCode} - ${line.productName}`,
    unitId: line.unitId,
    quantity: line.quantity,
    unitPrice: line.unitPrice ?? undefined,
    discountPercent: line.discountPercent ?? undefined,
    taxRateId: line.taxRateId ?? undefined,
    promotional: line.promotional,
    expectedDate: line.expectedDate ? dayjs(line.expectedDate) : undefined,
    lotNo: line.lotNo ?? undefined,
    lotOverrideReason: line.lotOverrideReason ?? undefined,
    serialNumbers: line.serialNumbers,
    description: line.description ?? undefined,
  };
}
