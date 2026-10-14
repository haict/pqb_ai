import { Card, Col, Descriptions, Row, Space, Typography } from 'antd';
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
  return (
    <>
      <Card
        variant="borderless"
        style={{
          marginBottom: 16,
          color: '#fff',
          background: `linear-gradient(120deg, ${BRAND.primary}, ${BRAND.primaryDark})`,
        }}
      >
        <Typography.Title level={3} style={{ color: '#fff', margin: 0 }}>
          Xin chào{user?.fullName ? `, ${user.fullName}` : ''}
        </Typography.Title>
        <Space style={{ marginTop: 8, color: 'rgba(255,255,255,0.9)' }}>
          Máy chủ ứng dụng:
          <span style={{ background: '#fff', borderRadius: 12, padding: '2px 10px' }}>
            <ServerStatusBadge />
          </span>
        </Space>
      </Card>
      {can('dashboard:view') && <DashboardPanel />}
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        Truy cập nhanh
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {groups.map((group) => (
          <Col key={group.key} xs={24} md={12} xl={8}>
            <Card
              size="small"
              title={
                <Space style={{ color: BRAND.primaryDark }}>
                  {GROUP_ICONS[group.key]}
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
