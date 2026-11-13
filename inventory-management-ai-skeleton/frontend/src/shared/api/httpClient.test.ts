import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { authClient, httpClient, newIdempotencyKey, uuidV4FromRandomBytes } from './httpClient';
import { sessionStore } from '../auth/sessionStore';

function response(config: InternalAxiosRequestConfig, status: number, data: unknown): AxiosResponse {
  return { data, status, statusText: '', headers: {}, config };
}

function unauthorized(config: InternalAxiosRequestConfig, code: string): AxiosError {
  return new AxiosError(
    '401',
    'ERR_BAD_REQUEST',
    config,
    null,
    response(config, 401, { success: false, code, message: 'Hết hạn', errors: [], requestId: null }),
  );
}

const user = {
  id: 1,
  username: 'admin',
  fullName: 'Admin',
  companyId: 1,
  defaultBranchId: 1,
  mustChangePassword: false,
  ownDataOnly: false,
  permissions: [],
  branches: [],
};

describe('httpClient', () => {
  let refreshCalls: number;
  let refreshFails: boolean;

  beforeEach(() => {
    refreshCalls = 0;
    refreshFails = false;
    sessionStore.clear();
    authClient.defaults.adapter = (async (config) => {
      refreshCalls++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      if (refreshFails) {
        throw unauthorized(config, 'SESSION_EXPIRED');
      }
      return response(config, 200, { success: true, data: { accessToken: 'new-token', user } });
    }) as AxiosAdapter;
    httpClient.defaults.adapter = (async (config) => {
      const authorization = AxiosHeaders.from(config.headers).get('Authorization');
      if (authorization !== 'Bearer new-token') {
        throw unauthorized(config, 'TOKEN_EXPIRED');
      }
      return response(config, 200, { success: true, data: config.url });
    }) as AxiosAdapter;
  });

  it('nhiều request cùng hết hạn chỉ làm mới phiên một lần rồi gửi lại với token mới', async () => {
    sessionStore.setAccessToken('expired-token');

    const results = await Promise.all([httpClient.get('/a'), httpClient.get('/b'), httpClient.get('/c')]);

    expect(results.map((result) => result.data.data)).toEqual(['/a', '/b', '/c']);
    expect(refreshCalls).toBe(1);
    expect(sessionStore.getAccessToken()).toBe('new-token');
  });

  it('làm mới thất bại thì phát sự kiện hết phiên và trả lỗi', async () => {
    sessionStore.setAccessToken('expired-token');
    refreshFails = true;
    const events: string[] = [];
    const unsubscribe = sessionStore.subscribe((event) => events.push(event));

    await expect(httpClient.get('/a')).rejects.toMatchObject({ status: 401 });

    expect(events).toEqual(['expired']);
    unsubscribe();
  });

  it('gắn chi nhánh làm việc vào header X-Branch-Id', async () => {
    sessionStore.setAccessToken('new-token');
    sessionStore.setWorkingBranchId(2);
    let branchHeader: string | undefined;
    httpClient.defaults.adapter = (async (config) => {
      branchHeader = AxiosHeaders.from(config.headers).get('X-Branch-Id') as string;
      return response(config, 200, { success: true, data: null });
    }) as AxiosAdapter;

    await httpClient.get('/x');

    expect(branchHeader).toBe('2');
  });
});

describe('newIdempotencyKey', () => {
  const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

  it('ghép UUID v4 đúng phiên bản, biến thể từ 16 byte', () => {
    expect(uuidV4FromRandomBytes(new Uint8Array(16).fill(0xff))).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
    expect(uuidV4FromRandomBytes(new Uint8Array(16))).toBe('00000000-0000-4000-8000-000000000000');
  });

  it('HTTP qua IP (không có crypto.randomUUID) vẫn sinh key, không lỗi', () => {
    const original = crypto.randomUUID;
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      const first = newIdempotencyKey();
      expect(first).toMatch(UUID_V4);
      expect(newIdempotencyKey()).not.toBe(first);
    } finally {
      Object.defineProperty(crypto, 'randomUUID', { value: original, configurable: true });
    }
  });
});
