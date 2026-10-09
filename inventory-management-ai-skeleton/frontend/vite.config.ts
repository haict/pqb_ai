/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Backend chạy với context path /erp. Đổi máy chủ bằng biến ERP_BACKEND_URL.
const backendUrl = process.env.ERP_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Frontend gọi cùng origin với đường dẫn /erp/... giống production, nên cookie refresh token
    // (Path=/erp/api/v1/auth) khớp đường dẫn trình duyệt. Vite chuyển tiếp /erp sang backend.
    proxy: {
      '/erp': backendUrl,
    },
  },
  build: {
    // Chunk Ant Design (Table, Form, DatePicker...) khoảng 1 MB, gzip ~313 kB, cache lâu dài vì ít thay đổi.
    // Ứng dụng nội bộ: chấp nhận ngưỡng 1100 kB. Code-split theo phân hệ khi số màn hình tăng.
    chunkSizeWarningLimit: 1100,
    rollupOptions: {
      output: {
        // Tách thư viện ít thay đổi để trình duyệt cache lâu dài.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          antd: ['antd', '@ant-design/icons'],
          query: ['@tanstack/react-query', 'axios', 'dayjs'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
