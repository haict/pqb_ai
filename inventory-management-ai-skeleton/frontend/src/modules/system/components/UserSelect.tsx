import { Select, type SelectProps } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { userLookupApi } from '../api';

type Props = Omit<SelectProps<number>, 'options' | 'showSearch' | 'filterOption' | 'onSearch'>;

/** Chọn người dùng đang hoạt động: tìm ở server theo tên đăng nhập / họ tên (tối đa 50 kết quả). */
export function UserSelect(props: Props) {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setKeyword(input.trim()), 300);
    return () => clearTimeout(timer);
  }, [input]);

  const query = useQuery({
    queryKey: ['users', 'lookup', keyword],
    queryFn: () => userLookupApi.search(keyword || undefined),
  });

  return (
    <Select<number>
      {...props}
      showSearch
      filterOption={false}
      onSearch={setInput}
      loading={query.isFetching}
      placeholder={props.placeholder ?? 'Tìm theo tên đăng nhập, họ tên'}
      options={(query.data ?? []).map((user) => ({
        value: user.id,
        label: `${user.fullName} (${user.username})`,
      }))}
    />
  );
}
