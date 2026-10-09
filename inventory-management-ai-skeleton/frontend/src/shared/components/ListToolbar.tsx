import { PlusOutlined } from '@ant-design/icons';
import { Button, Input, Select, Space } from 'antd';
import type { ReactNode } from 'react';
import type { RecordStatus } from './StatusTag';

interface Props {
  searchPlaceholder: string;
  onSearch(keyword: string): void;
  status?: RecordStatus;
  onStatusChange?(status: RecordStatus | undefined): void;
  extraFilters?: ReactNode;
  createLabel?: string;
  /** Không truyền thì không hiện nút tạo (không đủ quyền). */
  onCreate?(): void;
}

export function ListToolbar({
  searchPlaceholder,
  onSearch,
  status,
  onStatusChange,
  extraFilters,
  createLabel = 'Thêm mới',
  onCreate,
}: Props) {
  return (
    <div
      style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'space-between', marginBottom: 16 }}
    >
      <Space wrap>
        <Input.Search placeholder={searchPlaceholder} allowClear onSearch={onSearch} style={{ width: 260 }} />
        {extraFilters}
        {onStatusChange && (
          <Select
            placeholder="Trạng thái"
            allowClear
            value={status}
            onChange={onStatusChange}
            style={{ width: 170 }}
            options={[
              { value: 'ACTIVE', label: 'Hoạt động' },
              { value: 'INACTIVE', label: 'Ngừng hoạt động' },
            ]}
          />
        )}
      </Space>
      {onCreate && (
        <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>
          {createLabel}
        </Button>
      )}
    </div>
  );
}
