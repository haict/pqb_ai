import { App } from 'antd';
import { useState } from 'react';
import type { RecordStatus } from './StatusTag';

interface Item {
  status: RecordStatus;
}

/** Trạng thái và xử lý cho {@link ChangeStatusModal} của một danh sách. */
export function useStatusChange<T extends Item>(
  change: (item: T, status: RecordStatus, reason: string) => Promise<unknown>,
  labelOf: (item: T) => string,
  onDone: () => Promise<unknown>,
) {
  const { message } = App.useApp();
  const [target, setTarget] = useState<T | null>(null);

  return {
    open: (item: T) => setTarget(item),
    modalProps: {
      open: target !== null,
      label: target ? labelOf(target) : '',
      currentStatus: target?.status ?? ('ACTIVE' as RecordStatus),
      onCancel: () => setTarget(null),
      onConfirm: async (nextStatus: RecordStatus, reason: string) => {
        if (!target) {
          return;
        }
        try {
          await change(target, nextStatus, reason);
          message.success('Đã cập nhật trạng thái.');
          setTarget(null);
          await onDone();
        } catch (err) {
          message.error(err instanceof Error ? err.message : 'Không cập nhật được trạng thái.');
        }
      },
    },
  };
}
