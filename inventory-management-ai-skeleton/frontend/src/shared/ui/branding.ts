import { createContext, useContext } from 'react';
import defaultLogo from '../../assets/pqb-logo.svg';
import { API_BASE } from '../api/httpClient';

/** Tên, logo theo khách hàng (D-72), đặt bằng SQL ở sys_app_settings; chưa đặt thì dùng mặc định dưới đây. */
export interface BrandingSetting {
  appName: string | null;
  logoVersion: string | null;
}

export interface Branding {
  appName: string;
  logoUrl: string;
}

export const DEFAULT_BRANDING: Branding = {
  appName: 'Công nghệ y tế & giáo dục PQP',
  logoUrl: defaultLogo,
};

export function toBranding(setting: BrandingSetting): Branding {
  const appName = setting.appName?.trim() || DEFAULT_BRANDING.appName;
  if (!setting.logoVersion) {
    return { ...DEFAULT_BRANDING, appName };
  }
  return {
    appName,
    logoUrl: `${API_BASE}/public/branding/logo?v=${encodeURIComponent(setting.logoVersion)}`,
  };
}

export const BrandingContext = createContext<Branding>(DEFAULT_BRANDING);

export function useBranding(): Branding {
  return useContext(BrandingContext);
}
