import { useQuery } from '@tanstack/react-query';
import { fetchSystemHealth } from './systemHealth';

export function useSystemHealth() {
  return useQuery({
    queryKey: ['system', 'health'],
    queryFn: fetchSystemHealth,
    refetchInterval: 60_000,
  });
}
