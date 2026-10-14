import {
  ApartmentOutlined,
  DownOutlined,
  HomeOutlined,
  KeyOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown, Layout, Menu, Select, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, useCan } from '../shared/auth/authContext';
import { ServerStatusBadge } from '../shared/components/ServerStatusBadge';
import { BrandLogo } from './BrandLogo';
import { GROUP_ICONS, ITEM_ICONS } from './navIcons';
import { visibleNavGroups } from './navigation';
import { NotificationBell } from './NotificationBell';
import { BRAND } from './theme';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const can = useCan();
  const { user, workingBranchId, selectWorkingBranch, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const groups = visibleNavGroups(can);
  const activeGroup = groups.find((group) => group.items.some((item) => item.path === location.pathname));
  const menuItems: MenuProps['items'] = [
    { key: '/', icon: <HomeOutlined />, label: 'Trang chủ' },
    ...groups.map((group) => ({
      key: group.key,
      icon: GROUP_ICONS[group.key],
      label: group.label,
      children: group.items.map((item) => ({
        key: item.path,
        icon: ITEM_ICONS[item.path],
        label: item.label,
      })),
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
      <Sider
        theme="light"
        breakpoint="lg"
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        collapsedWidth={64}
        width={248}
        style={{
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflow: 'auto',
          borderRight: `1px solid ${BRAND.border}`,
        }}
      >
        <div
          style={{
            height: 56,
            padding: collapsed ? '0 15px' : '0 16px',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <BrandLogo compact={collapsed} />
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={activeGroup ? [activeGroup.key] : []}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderInlineEnd: 'none', padding: '0 4px 8px' }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap',
            borderBottom: `1px solid ${BRAND.border}`,
            boxShadow: '0 1px 4px rgba(15, 60, 70, 0.06)',
          }}
        >
          <Space size="small" wrap>
            <ApartmentOutlined style={{ color: BRAND.primary }} />
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
              <Button type="text" style={{ height: 40 }}>
                <Space size={8}>
                  <Avatar size="small" icon={<UserOutlined />} style={{ background: BRAND.primary }} />
                  {user?.fullName}
                  <DownOutlined style={{ fontSize: 10 }} />
                </Space>
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 20 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
