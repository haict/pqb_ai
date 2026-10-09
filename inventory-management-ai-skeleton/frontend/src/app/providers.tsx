import { useState, type ReactNode } from 'react';
import { App as AntApp, ConfigProvider } from 'antd';
import viVN from 'antd/locale/vi_VN';
import dayjs from 'dayjs';
import 'dayjs/locale/vi';
import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { createQueryClient } from './queryClient';

dayjs.locale('vi');

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={viVN} theme={{ token: { colorPrimary: '#1565c0', borderRadius: 4 } }}>
        <AntApp>
          <AuthProvider>{children}</AuthProvider>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
}
