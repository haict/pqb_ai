import { UploadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Descriptions, Input, Space, Tag, Typography, Upload } from 'antd';
import { useState } from 'react';
import { toApiError } from '../../../shared/api/apiError';
import { formatDate } from '../../inventory/labels';
import { LICENSE_QUERY_KEY, licenseApi } from '../api';
import type { LicenseState, LicenseStatus } from '../types';

const STATE_LABELS: Record<LicenseState, { label: string; color: string }> = {
  VALID: { label: 'Còn hiệu lực', color: 'green' },
  GRACE: { label: 'Trong thời gian ân hạn', color: 'orange' },
  EXPIRED: { label: 'Hết hạn - chỉ đọc', color: 'red' },
  MISSING: { label: 'Chưa có license - chỉ đọc', color: 'red' },
  INVALID: { label: 'Không hợp lệ - chỉ đọc', color: 'red' },
  CLOCK_TAMPERED: { label: 'Đồng hồ máy chủ bị lùi - chỉ đọc', color: 'red' },
  COMPANY_NOT_LICENSED: { label: 'Có công ty ngoài license - chỉ đọc', color: 'red' },
};

/**
 * Bản quyền phần mềm (D-59): xem trạng thái license và nạp license mới (file .lic do nhà cung cấp gửi). Hệ thống chỉ
 * đọc khi license không hiệu lực; trang này vẫn dùng được để nạp license.
 */
export function LicensePage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: LICENSE_QUERY_KEY, queryFn: licenseApi.status });
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const install = async () => {
    setSaving(true);
    setError(null);
    try {
      const result = await licenseApi.install(text.trim());
      queryClient.setQueryData(LICENSE_QUERY_KEY, result);
      setText('');
      message.success(`Đã nạp license. ${result.message}`);
    } catch (err) {
      setError(toApiError(err).fieldErrors[0]?.message ?? toApiError(err).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card loading={status.isPending}>
        <Typography.Title level={4}>Bản quyền phần mềm</Typography.Title>
        {status.data && <LicenseDetails status={status.data} />}
      </Card>
      <Card title="Nạp license mới">
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            Chọn file license (.lic) nhà cung cấp gửi hoặc dán nội dung license. License mới thay license đang
            dùng.
          </Typography.Text>
          <Upload
            accept=".lic,.txt"
            maxCount={1}
            showUploadList={false}
            beforeUpload={async (file) => {
              setText((await file.text()).trim());
              setError(null);
              return false;
            }}
          >
            <Button icon={<UploadOutlined />}>Chọn file license</Button>
          </Upload>
          <Input.TextArea
            rows={5}
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Nội dung license"
            style={{ fontFamily: 'monospace' }}
          />
          {error && <Alert type="error" showIcon message={error} />}
          <Button type="primary" disabled={!text.trim()} loading={saving} onClick={install}>
            Nạp license
          </Button>
        </Space>
      </Card>
    </Space>
  );
}

function LicenseDetails({ status }: { status: LicenseStatus }) {
  const state = STATE_LABELS[status.state];
  return (
    <>
      <Alert
        type={status.state === 'VALID' ? 'success' : status.writable ? 'warning' : 'error'}
        showIcon
        message={status.message}
        style={{ marginBottom: 16 }}
      />
      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="Trạng thái">
          <Tag color={state.color}>{state.label}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Mã license">{status.licenseId ?? ''}</Descriptions.Item>
        <Descriptions.Item label="Khách hàng">{status.customerName ?? ''}</Descriptions.Item>
        <Descriptions.Item label="Mã số thuế được dùng">{status.taxCodes.join(', ')}</Descriptions.Item>
        <Descriptions.Item label="Hiệu lực đến">{formatDate(status.validTo)}</Descriptions.Item>
        <Descriptions.Item label="Hết ân hạn">{formatDate(status.graceUntil)}</Descriptions.Item>
        <Descriptions.Item label="Còn lại">
          {status.daysRemaining === null ? '' : `${status.daysRemaining} ngày`}
        </Descriptions.Item>
        <Descriptions.Item label="Ngày nạp">
          {status.installedAt ? new Date(status.installedAt).toLocaleString('vi-VN') : ''}
        </Descriptions.Item>
        {status.unlicensedCompanies.length > 0 && (
          <Descriptions.Item label="Công ty ngoài license" span={2}>
            {status.unlicensedCompanies.join('; ')}
          </Descriptions.Item>
        )}
      </Descriptions>
    </>
  );
}
