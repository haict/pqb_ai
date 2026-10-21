import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { DocumentActions } from './DocumentActions';

function renderActions(actions: string[], onAction = vi.fn().mockResolvedValue(undefined)) {
  render(
    <App>
      <DocumentActions actions={actions} onAction={onAction} />
    </App>,
  );
  return onAction;
}

describe('DocumentActions', () => {
  it('chỉ hiện nút có trong allowedActions', () => {
    renderActions(['post', 'cancel']);
    expect(screen.getByRole('button', { name: 'Ghi sổ' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hủy' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument();
  });

  it('hủy bắt buộc nhập lý do trước khi gọi server', async () => {
    const onAction = renderActions(['cancel']);
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận' }));
    expect(await screen.findByText('Nhập lý do')).toBeInTheDocument();
    expect(onAction).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Lý do'), { target: { value: 'Lập sai kho' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
    await waitFor(() =>
      expect(onAction).toHaveBeenCalledWith('cancel', { reason: 'Lập sai kho' }, expect.any(String)),
    );
  });

  it('chế độ chỉ biểu tượng: nút không có chữ, tên ở aria-label; bỏ ghi sổ vẫn bắt buộc lý do', async () => {
    const onAction = vi.fn().mockResolvedValue(undefined);
    render(
      <App>
        <DocumentActions
          iconOnly
          actions={['unpost', 'reverse']}
          order={['unpost', 'reverse']}
          onAction={onAction}
        />
      </App>,
    );
    const unpost = screen.getByRole('button', { name: 'Bỏ ghi sổ' });
    expect(unpost).not.toHaveTextContent('Bỏ ghi sổ');
    expect(screen.getByRole('button', { name: 'Đảo chứng từ' })).toBeInTheDocument();
    fireEvent.click(unpost);
    fireEvent.change(await screen.findByLabelText('Lý do'), { target: { value: 'Sai giá' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
    await waitFor(() =>
      expect(onAction).toHaveBeenCalledWith('unpost', { reason: 'Sai giá' }, expect.any(String)),
    );
  });
});
