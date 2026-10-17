import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Select, Space } from 'antd';
import { useCompanies, useMyBranches } from '../lookups';
import { BRANCH_VISIBLE, whenBranchVisible } from '../../../shared/branch';

/**
 * Danh sách phạm vi dữ liệu (BR-SYS-04) trong form: mỗi dòng là toàn công ty hoặc một chi nhánh.
 * Chỉ chọn được trong phạm vi của người đang thao tác; server kiểm tra lại.
 */
export function DataScopeFields({ name = 'dataScopes' }: { name?: string }) {
  const companies = useCompanies();
  const branches = useMyBranches();

  return (
    <Form.List name={name}>
      {(fields, { add, remove }) => (
        <>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
              <Form.Item name={[field.name, 'type']} rules={[{ required: true, message: 'Chọn loại' }]}>
                <Select
                  style={{ width: 160 }}
                  options={[
                    { value: 'COMPANY', label: 'Toàn công ty' },
                    ...whenBranchVisible({ value: 'BRANCH', label: 'Chi nhánh' }),
                  ]}
                />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {({ getFieldValue }) => {
                  const type = getFieldValue([name, field.name, 'type']);
                  return (
                    <Form.Item
                      name={[field.name, 'scopeId']}
                      rules={[{ required: true, message: 'Chọn đơn vị' }]}
                    >
                      <Select
                        style={{ width: 260 }}
                        placeholder={type === 'COMPANY' ? 'Chọn công ty' : 'Chọn chi nhánh'}
                        options={type === 'COMPANY' ? companies.options : branches.options}
                      />
                    </Form.Item>
                  );
                }}
              </Form.Item>
              <MinusCircleOutlined aria-label="Xóa phạm vi" onClick={() => remove(field.name)} />
            </Space>
          ))}
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={() => add({ type: BRANCH_VISIBLE ? 'BRANCH' : 'COMPANY' })}
          >
            Thêm phạm vi
          </Button>
        </>
      )}
    </Form.List>
  );
}
