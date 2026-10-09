/** Định dạng phản hồi của backend (docs/04-api-specification.md mục 1.2, 1.3). */

export interface ApiResponse<T> {
  success: true;
  data: T;
  message: string | null;
  timestamp: string;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  success: false;
  code: string;
  message: string;
  errors: ApiFieldError[];
  requestId: string | null;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
