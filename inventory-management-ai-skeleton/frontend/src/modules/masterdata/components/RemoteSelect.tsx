import { Select, type SelectProps } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { PageResponse } from '../../../shared/api/types';

interface Props<T> extends Omit<SelectProps<number>, 'options' | 'showSearch' | 'filterOption' | 'onSearch'> {
  queryKey: string;
  fetch: (keyword: string | undefined) => Promise<PageResponse<T>>;
  toOption: (item: T) => { value: number; label: string };
  /** Nhãn cho giá trị đang chọn khi chưa có trong kết quả tìm kiếm (ví dụ khi mở form sửa). */
  selectedLabel?: string;
}

/** Ô chọn tìm ở server (danh mục lớn: hàng hóa, đối tác); không tải toàn bộ danh mục về client. */
export function RemoteSelect<T>({ queryKey, fetch, toOption, selectedLabel, ...props }: Props<T>) {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(input.trim()), 300);
    return () => clearTimeout(timer);
  }, [input]);

  const query = useQuery({
    queryKey: [queryKey, 'remote-select', keyword],
    queryFn: () => fetch(keyword || undefined),
  });
  const options = (query.data?.items ?? []).map(toOption);
  if (
    props.value !== undefined &&
    props.value !== null &&
    selectedLabel &&
    !options.some((option) => option.value === props.value)
  ) {
    options.unshift({ value: props.value as number, label: selectedLabel });
  }

  return (
    <Select<number>
      {...props}
      showSearch
      filterOption={false}
      onSearch={setInput}
      loading={query.isFetching}
      options={options}
    />
  );
}
