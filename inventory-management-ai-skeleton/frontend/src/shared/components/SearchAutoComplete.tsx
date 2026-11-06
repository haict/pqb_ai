import { useQuery } from '@tanstack/react-query';
import { AutoComplete, Input } from 'antd';
import { useEffect, useState } from 'react';
import type { PageResponse } from '../api/types';

export interface SuggestionOption {
  value: string;
  label: string;
}

interface Props<T> {
  queryKey: string;
  fetch: (keyword: string) => Promise<PageResponse<T>>;
  toOption: (item: T) => SuggestionOption;
  onSearch(keyword: string): void;
  placeholder?: string;
  width?: number;
}

/**
 * Ô tìm kiếm gợi ý khi gõ (auto complete): gợi ý lấy từ server theo từ khóa, chờ 300 ms sau lần gõ cuối để không gọi
 * API mỗi phím. Chọn gợi ý hoặc Enter thì lọc danh sách theo giá trị đó.
 */
export function SearchAutoComplete<T>({
  queryKey,
  fetch,
  toOption,
  onSearch,
  placeholder,
  width = 260,
}: Props<T>) {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(input.trim()), 300);
    return () => clearTimeout(timer);
  }, [input]);

  const query = useQuery({
    queryKey: [queryKey, 'suggest', keyword],
    queryFn: () => fetch(keyword),
    enabled: keyword.length > 0,
  });

  const search = (value: string) => {
    setInput(value);
    onSearch(value.trim());
  };

  return (
    <AutoComplete
      value={input}
      onChange={setInput}
      onSelect={search}
      options={keyword ? (query.data?.items ?? []).map(toOption) : []}
      style={{ width }}
      popupMatchSelectWidth={420}
    >
      <Input.Search placeholder={placeholder} allowClear loading={query.isFetching} onSearch={search} />
    </AutoComplete>
  );
}
