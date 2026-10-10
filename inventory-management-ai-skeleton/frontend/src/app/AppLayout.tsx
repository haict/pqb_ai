import {
  DownOutlined,
  HomeOutlined,
  KeyOutlined,
  LogoutOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Layout, Menu, Select, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, useCan } from '../shared/auth/authContext';
import { ServerStatusBadge } from '../shared/components/ServerStatusBadge';
import { visibleNavGroups } from './navigation';
import { NotificationBell } from './NotificationBell';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const can = useCan();
  const { user, workingBranchId, selectWorkingBranch, logout } = useAuth();

  const menuItems: MenuProps['items'] = [
    { key: '/', icon: <HomeOutlined />, label: 'Trang chủ' },
    ...visibleNavGroups(can).map((group) => ({
      key: group.key,
      icon: <SettingOutlined />,
      label: group.label,
      children: group.items.map((item) => ({ key: item.path, label: item.label })),
    })),
  ];

  const userMenu: MenuProps['items'] = [
    { key: 'change-password', icon: <KeyOutlined />, label: 'Đổi mật khẩu' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
  ];

  const onUserMenu: MenuProps['onClick'] = async ({ key }) => {
    if (key === 'change-password') {
      navigate('/change-password');
    } else if (key === 'logout') {
      await logout();
      navigate('/login', { replace: true });
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0} width={240}>
        <div style={{ color: '#fff', padding: '16px 20px', fontWeight: 600 }}>ERP Thiết bị y tế</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={visibleNavGroups(can).map((group) => group.key)}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <Space size="middle" wrap>
            <Typography.Text type="secondary">Chi nhánh làm việc:</Typography.Text>
            <Select
              aria-label="Chi nhánh làm việc"
              value={workingBranchId ?? undefined}
              onChange={selectWorkingBranch}
              style={{ minWidth: 220 }}
              options={(user?.branches ?? []).map((branch) => ({
                value: branch.id,
                label: `${branch.code} - ${branch.name}`,
              }))}
              notFoundContent="Chưa được gán chi nhánh"
            />
          </Space>
          <Space size="large" wrap>
            <ServerStatusBadge />
            <NotificationBell />
            <Dropdown menu={{ items: userMenu, onClick: onUserMenu }} trigger={['click']}>
              <Button type="text" icon={<UserOutlined />}>
                {user?.fullName} <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
