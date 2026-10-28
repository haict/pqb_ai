import { DeleteOutlined, PlusOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { toApiError } from '../../../shared/api/apiError';
import { useCan } from '../../../shared/auth/authContext';
import { formatDateTime } from '../../../shared/format';
import { assistantApi, useAiHealth } from '../api';
import { questionNote, sendKey, type PendingSend } from '../assistantChat';
import type { AssistantMessage } from '../types';

/** Bằng ai.chat.max-message-chars mặc định; server vẫn kiểm tra (ASSISTANT_MESSAGE_TOO_LONG). */
const MAX_QUESTION_CHARS = 2000;
const QUERY_KEY = ['assistant'];

/**
 * Nút "Trợ lý" trên thanh tiêu đề (D-64, AI-3): hỏi đáp cách dùng ERP từ tài liệu hướng dẫn. Ẩn khi thiếu quyền
 * assistant:use hoặc trợ lý không khả dụng (BR-AI-09). Trợ lý chỉ hướng dẫn, không xem dữ liệu và không thao tác hộ.
 */
export function AssistantButton() {
  const can = useCan();
  const allowed = can('assistant:use');
  const health = useAiHealth(allowed);
  const [open, setOpen] = useState(false);
  if (!allowed || !health.chatAvailable) {
    return null;
  }
  return (
    <>
      <Tooltip title="Trợ lý hướng dẫn sử dụng">
        <Button type="text" icon={<RobotOutlined style={{ fontSize: 18 }} />} onClick={() => setOpen(true)} />
      </Tooltip>
      {open && <AssistantDrawer onClose={() => setOpen(false)} />}
    </>
  );
}

function AssistantDrawer({ onClose }: { onClose(): void }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  // 'new': người dùng bấm "Hội thoại mới"; chưa chọn thì mở hội thoại gần nhất.
  const [chosen, setChosen] = useState<number | 'new' | undefined>();
  const [text, setText] = useState('');
  const [pending, setPending] = useState<PendingSend | null>(null);
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  const conversations = useQuery({ queryKey: [...QUERY_KEY, 'conversations'], queryFn: assistantApi.conversations });
  const conversationId = chosen === 'new' ? undefined : (chosen ?? conversations.data?.items[0]?.id);
  const messages = useQuery({
    queryKey: [...QUERY_KEY, 'messages', conversationId],
    queryFn: () => assistantApi.messages(conversationId as number),
    enabled: conversationId !== undefined,
  });

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages.data]);

  const send = useMutation({
    mutationFn: async (content: string) => {
      const id = conversationId ?? (await assistantApi.create(newIdempotencyKey())).id;
      setChosen(id);
      const attempt = sendKey(pending, id, content);
      setPending(attempt);
      return assistantApi.send(id, content, attempt.key);
    },
    onSuccess: () => {
      setPending(null);
      setText('');
      setError(null);
    },
    onError: (failure) => setError(toApiError(failure).message),
    onSettled: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  });

  const remove = useMutation({
    mutationFn: (id: number) => assistantApi.remove(id),
    onSuccess: () => {
      setChosen(undefined);
      void message.success('Đã xóa hội thoại.');
      return queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
    onError: (failure) => void message.error(toApiError(failure).message),
  });

  const submit = () => {
    const content = text.trim();
    if (content && !send.isPending) {
      send.mutate(content);
    }
  };

  return (
    <Drawer
      open
      onClose={onClose}
      width={520}
      title={
        <Space>
          <RobotOutlined /> Trợ lý hướng dẫn sử dụng
        </Space>
      }
      styles={{ body: { display: 'flex', flexDirection: 'column', gap: 8 } }}
    >
      <Space.Compact style={{ width: '100%' }}>
        <Select
          style={{ flex: 1, minWidth: 0 }}
          placeholder="Chưa có hội thoại"
          value={conversationId}
          loading={conversations.isFetching}
          onChange={setChosen}
          options={(conversations.data?.items ?? []).map((c) => ({
            value: c.id,
            label: `${c.title} · ${formatDateTime(c.lastMessageAt)}`,
          }))}
        />
        <Tooltip title="Hội thoại mới">
          <Button
            icon={<PlusOutlined />}
            onClick={() => {
              setChosen('new');
              setError(null);
            }}
            disabled={conversationId === undefined}
          />
        </Tooltip>
        <Popconfirm
          title="Xóa hội thoại này?"
          okText="Xóa"
          cancelText="Không"
          disabled={conversationId === undefined}
          onConfirm={() => remove.mutate(conversationId as number)}
        >
          <Button danger icon={<DeleteOutlined />} disabled={conversationId === undefined} />
        </Popconfirm>
      </Space.Compact>
      <Alert
        type="info"
        showIcon
        message="Trợ lý trả lời theo tài liệu hướng dẫn sử dụng; không xem dữ liệu, không thao tác thay bạn. Nội dung do AI tạo, cần kiểm tra lại."
      />
      <div style={{ flex: 1, overflowY: 'auto', minHeight: 200 }}>
        {conversationId === undefined || (messages.data && messages.data.items.length === 0) ? (
          <Empty description="Hỏi về cách dùng ERP, ví dụ: Lập đơn mua hàng thế nào?" />
        ) : messages.isLoading ? (
          <Spin />
        ) : (
          (messages.data?.items ?? []).map((item) => <MessageBubble key={item.id} message={item} />)
        )}
        {send.isPending && (
          <Typography.Text type="secondary">
            <Spin size="small" /> Trợ lý đang trả lời, có thể mất 15–60 giây…
          </Typography.Text>
        )}
        <div ref={bottom} />
      </div>
      {error && <Alert type="error" showIcon closable onClose={() => setError(null)} message={error} />}
      <Input.TextArea
        value={text}
        onChange={(event) => setText(event.target.value)}
        maxLength={MAX_QUESTION_CHARS}
        autoSize={{ minRows: 2, maxRows: 6 }}
        placeholder="Nhập câu hỏi (Ctrl + Enter để gửi)"
        disabled={send.isPending}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            submit();
          }
        }}
      />
      <Button type="primary" icon={<SendOutlined />} loading={send.isPending} disabled={!text.trim()} onClick={submit}>
        Gửi
      </Button>
    </Drawer>
  );
}

function MessageBubble({ message }: { message: AssistantMessage }) {
  const mine = message.role === 'USER';
  const note = questionNote(message);
  return (
    <div style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', margin: '6px 0' }}>
      <div
        style={{
          maxWidth: '88%',
          padding: '8px 12px',
          borderRadius: 10,
          background: mine ? '#e6f4ff' : '#f5f5f5',
          // Hiển thị văn bản thuần (không render HTML / Markdown, frontend.md mục 6).
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {message.content}
        {note && (
          <Typography.Text type={message.status === 'FAILED' ? 'danger' : 'secondary'} style={{ display: 'block' }}>
            {note}
          </Typography.Text>
        )}
        {!mine && message.citations.length > 0 && (
          <div style={{ marginTop: 6 }}>
            <Typography.Text type="secondary">Nguồn: </Typography.Text>
            {message.citations.map((citation, index) => (
              <Tag key={citation} style={{ marginTop: 4, whiteSpace: 'normal' }}>
                [{index + 1}] {citation}
              </Tag>
            ))}
          </div>
        )}
        {!mine && (
          <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 4 }}>
            Nội dung do AI tạo, cần kiểm tra lại · {formatDateTime(message.createdAt)}
          </Typography.Text>
        )}
      </div>
    </div>
  );
}
