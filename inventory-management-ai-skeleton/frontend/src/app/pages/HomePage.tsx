import {
  BarChartOutlined,
  MedicineBoxOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import { Button, Card, Col, Descriptions, Row, Space, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth, useCan } from '../../shared/auth/authContext';
import { ServerStatusBadge } from '../../shared/components/ServerStatusBadge';
import { DashboardPanel } from '../../modules/reporting/components/DashboardPanel';
import { GROUP_ICONS, ITEM_ICONS } from '../navIcons';
import { visibleNavGroups } from '../navigation';
import { BRAND } from '../theme';

/**
 * Trang chủ: lời chào, trạng thái máy chủ, dashboard điều hành (quyền `dashboard:view`, BR-UI-02) và lối tắt tới các
 * chức năng người dùng có quyền xem.
 */
export function HomePage() {
  const { user } = useAuth();
  const can = useCan();
  const navigate = useNavigate();
  const groups = visibleNavGroups(can);
  // Lối tắt trên banner: chỉ hiện chức năng người dùng có quyền xem.
  const shortcuts = [
    {
      path: '/reports/inventory-movement',
      label: 'Báo cáo nhập - xuất - tồn',
      icon: <BarChartOutlined />,
      permission: 'inventory-report:view',
    },
    {
      path: '/sales/orders',
      label: 'Đơn bán hàng',
      icon: <ShoppingCartOutlined />,
      permission: 'sales-order:view',
    },
    {
      path: '/purchasing/orders',
      label: 'Đơn mua hàng',
      icon: <ShoppingOutlined />,
      permission: 'purchase-order:view',
    },
  ].filter((shortcut) => can(shortcut.permission));
  return (
    <>
      <Card
        variant="borderless"
        style={{
          marginBottom: 20,
          color: '#fff',
          overflow: 'hidden',
          position: 'relative',
          background: `linear-gradient(120deg, #14b8a6 0%, ${BRAND.primaryDark} 55%, ${BRAND.primaryDeep} 100%)`,
          boxShadow: '0 12px 30px rgba(15, 118, 110, 0.25)',
        }}
        styles={{ body: { padding: '28px 32px' } }}
      >
        <MedicineBoxOutlined
          aria-hidden
          style={{
            position: 'absolute',
            right: 40,
            top: '50%',
            transform: 'translateY(-50%) rotate(-20deg)',
            fontSize: 150,
            color: 'rgba(255,255,255,0.12)',
          }}
        />
        <span
          style={{
            display: 'inline-block',
            background: 'rgba(255,255,255,0.92)',
            borderRadius: 999,
            padding: '2px 12px',
            fontSize: 12,
          }}
        >
          <ServerStatusBadge />
        </span>
        <Typography.Title level={2} style={{ color: '#fff', margin: '14px 0 6px' }}>
          {greeting()}
          {user?.fullName ? `, ${user.fullName}` : ''}
        </Typography.Title>
        <Typography.Paragraph style={{ color: 'rgba(255,255,255,0.88)', maxWidth: 620, marginBottom: 20 }}>
          Theo dõi tồn kho, đơn mua, đơn bán, công nợ và báo cáo của doanh nghiệp thiết bị - vật tư y tế trên
          một màn hình.
        </Typography.Paragraph>
        <Space wrap>
          {shortcuts.map((shortcut, index) => (
            <Button
              key={shortcut.path}
              icon={shortcut.icon}
              onClick={() => navigate(shortcut.path)}
              style={
                index === 0
                  ? { background: '#fff', color: BRAND.primaryDeep, border: 'none' }
                  : {
                      background: 'rgba(255,255,255,0.14)',
                      color: '#fff',
                      borderColor: 'rgba(255,255,255,0.35)',
                    }
              }
            >
              {shortcut.label}
            </Button>
          ))}
        </Space>
      </Card>
      {can('dashboard:view') && <DashboardPanel />}
      <Typography.Title level={5} style={{ marginTop: 4 }}>
        Truy cập nhanh
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {groups.map((group) => (
          <Col key={group.key} xs={24} md={12} xl={8}>
            <Card
              size="small"
              title={
                <Space size={10} style={{ color: BRAND.text }}>
                  <span
                    style={{
                      width: 30,
                      height: 30,
                      borderRadius: 8,
                      display: 'grid',
                      placeItems: 'center',
                      color: BRAND.primaryDark,
                      background: BRAND.primarySoft,
                    }}
                  >
                    {GROUP_ICONS[group.key]}
                  </span>
                  {group.label}
                </Space>
              }
              style={{ height: '100%' }}
            >
              <Descriptions column={1} size="small">
                {group.items.map((item) => (
                  <Descriptions.Item key={item.path}>
                    <Typography.Link onClick={() => navigate(item.path)}>
                      <Space size={6}>
                        {ITEM_ICONS[item.path]}
                        {item.label}
                      </Space>
                    </Typography.Link>
                  </Descriptions.Item>
                ))}
              </Descriptions>
            </Card>
          </Col>
        ))}
      </Row>
    </>
  );
}

/** Lời chào theo giờ trong ngày (giờ máy người dùng). */
function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 11) {
    return 'Chào buổi sáng';
  }
  if (hour < 18) {
    return 'Chào buổi chiều';
  }
  return 'Chào buổi tối';
}
