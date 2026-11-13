import { Client } from '@stomp/stompjs';
import { FormEvent, useEffect, useRef, useState } from 'react';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const WS = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws';

type Room = { id: string; name: string; type: string; members: string[] };
type ChatMessage = { id: string; roomId: string; sender: string; content: string; createdAt: string };
type AuthResponse = { token: string; username: string };

async function request<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...init?.headers,
    },
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      window.dispatchEvent(new Event('chat-auth-invalid'));
    }
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? body?.message ?? `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export default function App() {
  const [session, setSession] = useState<AuthResponse | null>(() => {
    const token = localStorage.getItem('chat-token');
    const username = localStorage.getItem('chat-username');
    return token && username ? { token, username } : null;
  });
  const [registering, setRegistering] = useState(false);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [activeRoom, setActiveRoom] = useState<Room | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [onlineUsers, setOnlineUsers] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [requestedRoomId, setRequestedRoomId] = useState(
    () => new URLSearchParams(window.location.search).get('room'),
  );
  const clientRef = useRef<Client | null>(null);
  const messageEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handlePopState = () => {
      setRequestedRoomId(new URLSearchParams(window.location.search).get('room'));
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    const handleInvalidSession = () => {
      localStorage.removeItem('chat-token');
      localStorage.removeItem('chat-username');
      setSession(null);
      setActiveRoom(null);
      setMessages([]);
      setError('Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.');
    };
    window.addEventListener('chat-auth-invalid', handleInvalidSession);
    return () => window.removeEventListener('chat-auth-invalid', handleInvalidSession);
  }, []);

  useEffect(() => {
    if (!session) return;
    let active = true;
    request<Room[]>('/api/rooms', session.token)
      .then((items) => { if (active) setRooms(items); })
      .catch((cause: Error) => setError(cause.message));

    const client = new Client({
      brokerURL: WS,
      connectHeaders: { Authorization: `Bearer ${session.token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe('/topic/presence', (frame) => setOnlineUsers(JSON.parse(frame.body) as string[]));
        if (activeRoom) {
          client.subscribe(`/topic/rooms/${activeRoom.id}`, (frame) => {
            const incoming = JSON.parse(frame.body) as ChatMessage;
            setMessages((current) => current.some((message) => message.id === incoming.id)
              ? current : [...current, incoming]);
          });
        }
      },
      onStompError: (frame) => setError(frame.headers.message ?? 'WebSocket error'),
      onWebSocketError: () => setError('Không thể kết nối realtime. Hãy kiểm tra backend.'),
    });
    client.activate();
    clientRef.current = client;
    return () => {
      active = false;
      void client.deactivate();
      clientRef.current = null;
    };
  }, [session, activeRoom]);

  useEffect(() => {
    if (!session || !requestedRoomId) return;
    let active = true;
    request<Room>(`/api/rooms/${encodeURIComponent(requestedRoomId)}`, session.token)
      .then((room) => {
        if (active) {
          setActiveRoom(room);
          setError('');
        }
      })
      .catch((cause: Error) => {
        if (active) {
          setActiveRoom(null);
          setError(`Không thể mở phòng chat: ${cause.message}`);
        }
      });
    return () => { active = false; };
  }, [session, requestedRoomId]);

  useEffect(() => {
    if (!session || !activeRoom) return;
    request<ChatMessage[]>(`/api/rooms/${activeRoom.id}/messages`, session.token)
      .then(setMessages)
      .catch((cause: Error) => setError(cause.message));
  }, [session, activeRoom]);

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    const form = new FormData(event.currentTarget);
    const payload = {
      username: String(form.get('username') ?? ''),
      password: String(form.get('password') ?? ''),
      ...(registering ? { email: String(form.get('email') ?? '') } : {}),
    };
    try {
      const result = await fetch(`${API}/api/auth/${registering ? 'register' : 'login'}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!result.ok) {
        const body = await result.json().catch(() => null);
        throw new Error(body?.detail ?? body?.message ?? `Đăng nhập thất bại (${result.status})`);
      }
      const auth = await result.json() as AuthResponse;
      localStorage.setItem('chat-token', auth.token);
      localStorage.setItem('chat-username', auth.username);
      setSession(auth);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Có lỗi xảy ra');
    }
  }

  async function createRoom(type: 'direct' | 'group') {
    const membersInput = window.prompt('Nhập username thành viên, cách nhau bằng dấu phẩy:');
    if (!membersInput) return;
    const members = membersInput.split(',').map((name) => name.trim()).filter(Boolean);
    const name = window.prompt('Tên phòng:')?.trim();
    if (!name) return;
    try {
      const room = await request<Room>('/api/rooms', session!.token, {
        method: 'POST',
        body: JSON.stringify({ name, type, members }),
      });
      setRooms((current) => [room, ...current]);
      openRoom(room);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể tạo phòng');
    }
  }

  function openRoom(room: Room) {
    const url = new URL(window.location.href);
    url.searchParams.set('room', room.id);
    window.history.pushState({}, '', url);
    setRequestedRoomId(room.id);
    setActiveRoom(room);
    setMessages([]);
    setError('');
  }

  async function shareRoom() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setError('Đã sao chép link phòng. Chỉ thành viên mới mở được.');
    } catch {
      setError('Không thể sao chép link. Hãy sao chép URL trên thanh địa chỉ.');
    }
  }

  function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const content = String(new FormData(form).get('content') ?? '').trim();
    if (!content || !activeRoom || !clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ roomId: activeRoom.id, content }),
    });
    form.reset();
  }

  function signOut() {
    localStorage.removeItem('chat-token');
    localStorage.removeItem('chat-username');
    setSession(null);
    setRooms([]);
    setActiveRoom(null);
    setMessages([]);
    setRequestedRoomId(null);
    const url = new URL(window.location.href);
    url.searchParams.delete('room');
    window.history.replaceState({}, '', url);
  }

  if (!session) {
    return (
      <main className="auth-page">
        <form className="auth-card" onSubmit={authenticate}>
          <div className="brand-mark">C</div>
          <h1>{registering ? 'Tạo tài khoản' : 'Chào mừng trở lại'}</h1>
          <p>Đăng nhập để trò chuyện cùng mọi người.</p>
          <label>Tên đăng nhập<input name="username" required minLength={registering ? 3 : undefined} /></label>
          {registering && <label>Email<input name="email" type="email" required /></label>}
          <label>Mật khẩu<input name="password" type="password" required minLength={registering ? 8 : undefined} /></label>
          {error && <div className="error">{error}</div>}
          <button className="primary" type="submit">{registering ? 'Đăng ký' : 'Đăng nhập'}</button>
          <button className="text-button" type="button" onClick={() => setRegistering(!registering)}>
            {registering ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="brand-lockup"><div className="brand-mark small">C</div><strong>Chatspace</strong></div>
          <button className="icon-button" title="Tạo phòng nhóm" onClick={() => void createRoom('group')}>＋</button>
        </div>
        <div className="section-title">TIN NHẮN</div>
        <button className="new-direct" onClick={() => void createRoom('direct')}>＋ Tạo cuộc trò chuyện</button>
        <nav className="room-list">
          {rooms.map((room) => (
            <button key={room.id} className={`room-item ${activeRoom?.id === room.id ? 'selected' : ''}`} onClick={() => openRoom(room)}>
              <span className="room-avatar">{room.type === 'group' ? '◉' : room.name.slice(0, 1).toUpperCase()}</span>
              <span className="room-name">{room.name}</span>
              {room.type === 'group' && <span className="member-count">{room.members.length}</span>}
            </button>
          ))}
          {rooms.length === 0 && <p className="empty-sidebar">Chưa có cuộc trò chuyện</p>}
        </nav>
        <div className="profile">
          <div className="avatar">{session.username.slice(0, 1).toUpperCase()}</div>
          <div className="profile-copy"><strong>{session.username}</strong><span>Đang hoạt động</span></div>
          <button className="icon-button" title="Đăng xuất" onClick={signOut}>↗</button>
        </div>
      </aside>
      <section className="chat-panel">
        {activeRoom ? (
          <>
            <header className="chat-header">
              <div className="room-avatar large">{activeRoom.type === 'group' ? '◉' : activeRoom.name.slice(0, 1).toUpperCase()}</div>
              <div className="room-heading"><h2>{activeRoom.name}</h2><p>{activeRoom.members.length} thành viên · {activeRoom.members.filter((user) => onlineUsers.includes(user)).length} đang online</p></div>
              <button className="icon-button share-room" title="Sao chép link phòng" onClick={() => void shareRoom()}>↗</button>
            </header>
            <div className="messages">
              {messages.map((message) => (
                <article key={message.id} className={`message ${message.sender === session.username ? 'mine' : ''}`}>
                  <div className="message-avatar">{message.sender.slice(0, 1).toUpperCase()}</div>
                  <div className="message-body">
                    <div className="message-meta"><strong>{message.sender}</strong><time>{new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time></div>
                    <p>{message.content}</p>
                  </div>
                </article>
              ))}
              <div ref={messageEndRef} />
            </div>
            <form className="composer" onSubmit={sendMessage}>
              <input name="content" maxLength={4000} placeholder={`Nhắn tin trong ${activeRoom.name}...`} autoComplete="off" />
              <button className="send-button" type="submit" aria-label="Gửi tin nhắn">➤</button>
            </form>
          </>
        ) : (
          <div className="welcome"><div className="welcome-icon">✳</div><h2>Bắt đầu cuộc trò chuyện</h2><p>Tạo phòng mới hoặc chọn một cuộc trò chuyện ở bên trái.</p></div>
        )}
        {error && <div className="toast-error">{error}<button onClick={() => setError('')}>×</button></div>}
      </section>
    </main>
  );
}
