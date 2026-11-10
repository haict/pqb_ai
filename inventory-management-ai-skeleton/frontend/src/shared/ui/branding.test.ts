import { DEFAULT_BRANDING, toBranding } from './branding';

describe('toBranding (D-72)', () => {
  it('chưa cấu hình thì dùng tên, logo mặc định', () => {
    expect(toBranding({ appName: null, logoVersion: null })).toEqual(DEFAULT_BRANDING);
    expect(toBranding({ appName: '   ', logoVersion: null })).toEqual(DEFAULT_BRANDING);
  });

  it('có cấu hình thì dùng tên khách hàng và URL logo theo phiên bản', () => {
    expect(toBranding({ appName: ' Công ty ABC ', logoVersion: 'k3x9' })).toEqual({
      appName: 'Công ty ABC',
      logoUrl: '/erp/api/v1/public/branding/logo?v=k3x9',
    });
  });
});
