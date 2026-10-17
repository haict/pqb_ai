import type { ThemeConfig } from 'antd';

/**
 * Bảng màu giao diện: xanh ngọc y tế (teal) làm màu chủ đạo, nền xám rất nhạt, thẻ trắng bo góc lớn, bóng mềm.
 * Màu dùng trong CSS (styles.css) đặt cùng giá trị qua biến --app-*.
 */
export const BRAND = {
  primary: '#0d9488',
  primaryDark: '#0f766e',
  primaryDeep: '#115e59',
  primarySoft: '#e6f6f4',
  background: '#f5f7fa',
  border: '#e8edf2',
  text: '#1f2937',
  muted: '#6b7280',
  gradient: 'linear-gradient(135deg, #14b8a6 0%, #0f766e 100%)',
};

export const APP_THEME: ThemeConfig = {
  token: {
    colorPrimary: BRAND.primary,
    colorInfo: BRAND.primary,
    colorLink: BRAND.primaryDark,
    colorSuccess: '#16a34a',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorText: BRAND.text,
    colorTextSecondary: BRAND.muted,
    colorBgLayout: BRAND.background,
    colorBorder: '#dfe5ec',
    colorBorderSecondary: BRAND.border,
    fontFamily:
      "Inter, 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, 'Helvetica Neue', Arial, sans-serif",
    borderRadius: 8,
    borderRadiusLG: 14,
    controlHeight: 36,
    boxShadowTertiary: '0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06)',
  },
  components: {
    Layout: {
      siderBg: '#ffffff',
      headerBg: '#ffffff',
      bodyBg: BRAND.background,
      headerHeight: 64,
      headerPadding: '0 24px',
      lightTriggerBg: '#ffffff',
      lightTriggerColor: BRAND.primaryDark,
    },
    Menu: {
      itemSelectedBg: BRAND.primarySoft,
      itemSelectedColor: BRAND.primaryDark,
      itemColor: '#374151',
      itemHoverBg: '#f1f5f9',
      itemHoverColor: BRAND.text,
      itemBorderRadius: 10,
      itemHeight: 40,
      itemMarginInline: 8,
      subMenuItemBg: '#ffffff',
      groupTitleColor: '#9ca3af',
      iconSize: 15,
    },
    Button: {
      fontWeight: 500,
      primaryShadow: '0 4px 12px rgba(13, 148, 136, 0.25)',
      defaultShadow: '0 1px 2px rgba(16, 24, 40, 0.05)',
      paddingInline: 16,
    },
    Card: {
      headerFontSize: 15,
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      headerSplitColor: 'transparent',
      rowHoverBg: '#f0fdfa',
      borderColor: '#eef2f6',
    },
    Tag: {
      borderRadiusSM: 6,
    },
    Modal: {
      titleFontSize: 17,
    },
  },
};
