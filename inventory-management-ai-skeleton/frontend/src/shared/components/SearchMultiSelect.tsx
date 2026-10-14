import { useQuery } from '@tanstack/react-query';
import { Select } from 'antd';
import { useEffect, useState } from 'react';
import type { PageResponse } from '../api/types';

export interface LabeledValue {
  value: number;
  label: string;
}

interface Props<T> {
  queryKey: string;
  fetch: (keyword: string | undefined) => Promise<PageResponse<T>>;
  toOption: (item: T) => LabeledValue;
  value?: LabeledValue[];
  onChange?: (value: LabeledValue[]) => void;
  placeholder?: string;
}

/**
 * Chọn nhiều mục từ danh mục lớn, tìm ở server; giữ nhãn mục đã chọn khi đổi từ khóa (labelInValue) nên không tải
 * toàn bộ danh mục về client.
 */
export function SearchMultiSelect<T>({ queryKey, fetch, toOption, value, onChange, placeholder }: Props<T>) {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(input.trim()), 300);
    return () => clearTimeout(timer);
  }, [input]);

  const query = useQuery({
    queryKey: [queryKey, 'multi-select', keyword],
    queryFn: () => fetch(keyword || undefined),
  });

  return (
    <Select<LabeledValue[]>
      mode="multiple"
      labelInValue
      allowClear
      showSearch
      filterOption={false}
      onSearch={setInput}
      loading={query.isFetching}
      placeholder={placeholder}
      value={value}
      onChange={(selected) =>
        onChange?.(selected.map((item) => ({ value: item.value, label: String(item.label) })))
      }
      options={(query.data?.items ?? []).map(toOption)}
      maxTagCount="responsive"
    />
  );
}
