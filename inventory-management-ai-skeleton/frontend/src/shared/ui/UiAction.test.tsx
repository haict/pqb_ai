import { render, screen } from '@testing-library/react';
import { UiAction } from './UiAction';
import { toUiFeatures, UiFeaturesContext } from './uiFeatures';

describe('UiAction (D-72)', () => {
  it('mặc định hiện nút khi không có cấu hình', () => {
    render(<UiAction id="excel-import">Nhập Excel</UiAction>);
    expect(screen.getByText('Nhập Excel')).toBeInTheDocument();
  });

  it('ẩn nút khi cấu hình tắt', () => {
    const features = toUiFeatures([{ key: 'action:excel-import', enabled: false, customLabel: null }]);
    render(
      <UiFeaturesContext.Provider value={features}>
        <UiAction id="excel-import">Nhập Excel</UiAction>
        <UiAction id="excel-export">Xuất Excel</UiAction>
      </UiFeaturesContext.Provider>,
    );
    expect(screen.queryByText('Nhập Excel')).not.toBeInTheDocument();
    expect(screen.getByText('Xuất Excel')).toBeInTheDocument();
  });
});
