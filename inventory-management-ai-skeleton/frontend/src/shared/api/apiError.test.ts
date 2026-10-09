import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { ApiError, NETWORK_ERROR, toApiError, UNKNOWN_ERROR } from './apiError';

function axiosErrorWithResponse(status: number, data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const response = { status, data, statusText: '', headers: {}, config } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, response);
}

describe('toApiError', () => {
  it('đọc mã lỗi, thông báo, lỗi theo trường và requestId từ phản hồi chuẩn của backend', () => {
    const error = toApiError(
      axiosErrorWithResponse(422, {
        success: false,
        code: 'INSUFFICIENT_STOCK',
        message: 'Tồn khả dụng không đủ.',
        errors: [{ field: 'lines[0].quantity', message: 'Phải lớn hơn 0' }],
        requestId: 'req-1',
        timestamp: '2026-10-09T00:00:00Z',
      }),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe('INSUFFICIENT_STOCK');
    expect(error.message).toBe('Tồn khả dụng không đủ.');
    expect(error.fieldErrors).toEqual([{ field: 'lines[0].quantity', message: 'Phải lớn hơn 0' }]);
    expect(error.requestId).toBe('req-1');
  });

  it('báo lỗi mạng bằng tiếng Việt khi không có phản hồi', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'));

    expect(error.code).toBe(NETWORK_ERROR);
    expect(error.status).toBeNull();
    expect(error.message).toContain('Không kết nối được máy chủ');
  });

  it('không hiển thị nội dung kỹ thuật khi phản hồi không đúng định dạng', () => {
    const error = toApiError(axiosErrorWithResponse(502, '<html>Bad Gateway nginx</html>'));

    expect(error.code).toBe(UNKNOWN_ERROR);
    expect(error.message).not.toContain('nginx');
  });
});
