import { useQuery } from '@tanstack/react-query';
import { Alert, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCan } from '../shared/auth/authContext';
import { LICENSE_QUERY_KEY, licenseApi } from '../modules/system/api';

/** Số ngày trước khi hết ân hạn bắt đầu nhắc gia hạn trên mọi màn hình. */
const REMIND_DAYS = 15;

/**
 * Banner bản quyền (D-59) trên đầu mọi màn hình: báo chế độ chỉ đọc, thời gian ân hạn, hoặc sắp hết hạn. Server vẫn
 * là nơi chặn ghi dữ liệu; banner chỉ để người dùng biết lý do.
 */
export function LicenseBanner() {
  const can = useCan();
  const navigate = useNavigate();
  const { data } = useQuery({
    queryKey: LICENSE_QUERY_KEY,
    queryFn: licenseApi.status,
    staleTime: 5 * 60_000,
    refetchInterval: 30 * 60_000,
  });
  if (!data) {
    return null;
  }
  const remind = data.state === 'VALID' && data.daysRemaining !== null && data.daysRemaining <= REMIND_DAYS;
  if (data.state === 'VALID' && !remind) {
    return null;
  }
  return (
    <Alert
      type={data.writable ? 'warning' : 'error'}
      showIcon
      banner
      style={{ marginBottom: 16, borderRadius: 10 }}
      message={data.message}
      action={
        can('license:manage') && (
          <Button size="small" onClick={() => navigate('/system/license')}>
            Nạp license
          </Button>
        )
      }
    />
  );
}
