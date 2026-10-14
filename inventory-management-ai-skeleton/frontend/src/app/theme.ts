import type { ThemeConfig } from 'antd';

/** Bảng màu giao diện: xanh ngọc y tế làm màu chủ đạo, nền xám xanh nhạt, menu trái nền sáng. */
export const BRAND = {
  primary: '#0e7c86',
  primaryDark: '#0a5c64',
  primarySoft: '#e6f4f5',
  background: '#f4f7f9',
  border: '#e3eaee',
};

export const APP_THEME: ThemeConfig = {
  token: {
    colorPrimary: BRAND.primary,
    colorInfo: BRAND.primary,
    colorLink: BRAND.primary,
    colorSuccess: '#2e9d5b',
    colorWarning: '#d98a00',
    colorError: '#d64545',
    colorBgLayout: BRAND.background,
    colorBorderSecondary: BRAND.border,
    borderRadius: 6,
  },
  components: {
    Layout: {
      siderBg: '#ffffff',
      headerBg: '#ffffff',
      bodyBg: BRAND.background,
      headerHeight: 56,
      headerPadding: '0 16px',
      lightTriggerBg: '#ffffff',
      lightTriggerColor: BRAND.primary,
    },
    Menu: {
      itemSelectedBg: BRAND.primarySoft,
      itemSelectedColor: BRAND.primary,
      itemHoverBg: '#f2f8f9',
      itemBorderRadius: 6,
      itemHeight: 38,
      subMenuItemBg: '#ffffff',
    },
    Table: {
      headerBg: '#eef4f6',
      rowHoverBg: '#f5fafb',
    },
    Card: {
      borderRadiusLG: 10,
    },
  },
};
