import { Col, DatePicker, Form, Select } from 'antd';
import dayjs from 'dayjs';
import { PERIOD_PRESETS, periodRange, type PeriodPreset, type PeriodValues } from '../reportPeriod';

/** Hai ô "Kỳ báo cáo" và "Từ ngày - Đến ngày" (như màn hình tham số hiện trạng); chọn ngày tay chuyển sang Tùy chọn. */
export function PeriodFields() {
  const form = Form.useFormInstance<PeriodValues>();
  return (
    <>
      <Col xs={24} md={6}>
        <Form.Item name="preset" label="Kỳ báo cáo">
          <Select
            onChange={(preset: PeriodPreset) =>
              form.setFieldValue('range', periodRange(preset, dayjs(), form.getFieldValue('range')))
            }
            options={(Object.keys(PERIOD_PRESETS) as PeriodPreset[]).map((value) => ({
              value,
              label: PERIOD_PRESETS[value],
            }))}
          />
        </Form.Item>
      </Col>
      <Col xs={24} md={8}>
        <Form.Item
          name="range"
          label="Từ ngày - Đến ngày"
          rules={[{ required: true, message: 'Chọn khoảng ngày' }]}
        >
          <DatePicker.RangePicker
            format="DD/MM/YYYY"
            allowClear={false}
            style={{ width: '100%' }}
            onChange={() => form.setFieldValue('preset', 'CUSTOM')}
          />
        </Form.Item>
      </Col>
    </>
  );
}
