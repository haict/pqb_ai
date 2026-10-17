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
import { BRANCH_VISIBLE } from '../shared/branch';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const can = useCan();
  const { user, workingBranchId, selectWorkingBranch, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const groups = visibleNavGroups(can);
  const activeGroup = groups.find((group) => group.items.some((item) => item.path === location.pathname));
  // Trang chi tiết (ví dụ /inventory/documents/12) thuộc mục menu có đường dẫn là tiền tố.
  const current = groups
    .flatMap((group) => group.items.map((item) => ({ group, item })))
    .filter(({ item }) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`))
    .sort((a, b) => b.item.path.length - a.item.path.length)[0];
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
        className="app-sider"
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
            height: 64,
            padding: collapsed ? '0 15px' : '0 20px',
            display: 'flex',
            alignItems: 'center',
            borderBottom: `1px solid ${BRAND.border}`,
          }}
        >
          <BrandLogo compact={collapsed} />
        </div>
        {!collapsed && <div className="app-sider-label">Menu chính</div>}
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={activeGroup ? [activeGroup.key] : []}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderInlineEnd: 'none', paddingBottom: 8, marginTop: collapsed ? 8 : 0 }}
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
          }}
        >
          <Space size="middle" wrap>
            <div style={{ lineHeight: 1.25 }}>
              {current && (
                <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                  {current.group.label}
                </Typography.Text>
              )}
              <Typography.Text strong style={{ fontSize: current ? 18 : 20, color: BRAND.text }}>
                {current ? current.item.label : 'Trang chủ'}
              </Typography.Text>
            </div>
            {BRANCH_VISIBLE && (
              <Space size="small">
                <ApartmentOutlined style={{ color: BRAND.primary }} />
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
            )}
          </Space>
          <Space size="middle" wrap>
            <span className="app-status-pill">
              <ServerStatusBadge />
            </span>
            <NotificationBell />
            <span style={{ width: 1, height: 28, background: BRAND.border }} />
            <Dropdown menu={{ items: userMenu, onClick: onUserMenu }} trigger={['click']}>
              <Button type="text" style={{ height: 48, padding: '0 8px' }}>
                <Space size={10}>
                  <Avatar style={{ background: BRAND.gradient, fontWeight: 600 }}>
                    {initials(user?.fullName) || <UserOutlined />}
                  </Avatar>
                  <span style={{ textAlign: 'left', lineHeight: 1.25 }}>
                    <span style={{ display: 'block', fontWeight: 600 }}>{user?.fullName}</span>
                    <span style={{ display: 'block', fontSize: 12, color: BRAND.muted }}>
                      {user?.username}
                    </span>
                  </span>
                  <DownOutlined style={{ fontSize: 10, color: BRAND.muted }} />
                </Space>
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

/** Chữ cái đầu của họ tên (tối đa 2 ký tự) cho ảnh đại diện. */
function initials(fullName: string | undefined): string {
  const words = (fullName ?? '').trim().split(/\s+/).filter(Boolean);
  const picked = words.length > 1 ? [words[0], words[words.length - 1]] : words;
  return picked.map((word) => word?.charAt(0).toUpperCase() ?? '').join('');
}
