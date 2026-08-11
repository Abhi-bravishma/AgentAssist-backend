import { useEffect, useState } from 'react';
import { admin, kb } from './api';
import ChatPage from './pages/ChatPage';
import DocumentsPage from './pages/DocumentsPage';
import PromptsPage from './pages/PromptsPage';
import RegistryPage from './pages/RegistryPage';
import SettingsPage from './pages/SettingsPage';

type Tab = 'chat' | 'documents' | 'prompts' | 'registry' | 'settings';

export default function App() {
  const [tab, setTab] = useState<Tab>(
    (window.location.hash.replace('#', '') as Tab) || 'documents',
  );
  const [kbUp, setKbUp] = useState<boolean | null>(null);
  const [provider, setProvider] = useState<string>('…');

  useEffect(() => {
    if (window.location.hash.replace('#', '') !== tab) {
      window.location.hash = tab;
    }
  }, [tab]);

  // Follow hash changes (cross-page jumps like Setup -> AI Instructions, browser Back)
  useEffect(() => {
    const onHash = () => {
      const t = window.location.hash.replace('#', '') as Tab;
      if (['chat', 'documents', 'prompts', 'registry', 'settings'].includes(t)) setTab(t);
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const refreshHeader = async () => {
    try {
      const s = await kb.status();
      setKbUp(s.enabled);
    } catch {
      setKbUp(false);
    }
    try {
      const p = await admin.aiProvider();
      setProvider(p.active);
    } catch {
      setProvider('?');
    }
  };

  useEffect(() => {
    refreshHeader();
  }, []);

  const icons: Record<Tab, JSX.Element> = {
    chat: (
      <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    ),
    documents: (
      <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
      </svg>
    ),
    prompts: (
      <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z" />
      </svg>
    ),
    registry: (
      <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6" />
      </svg>
    ),
    settings: (
      <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
      </svg>
    ),
  };

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="logo">✦</span>
          <span>Agent Assist<small>Admin portal</small></span>
        </div>
        <nav className="side-nav">
          {([
            ['chat', 'Test chat'],
            ['documents', 'Documents'],
            ['prompts', 'AI Instructions'],
            ['registry', 'Setup'],
            ['settings', 'Settings'],
          ] as [Tab, string][]).map(([t, label]) => (
            <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
              {icons[t]}{label}
            </button>
          ))}
        </nav>
        <div className="side-foot">
          <span className="tag">AI engine: {provider}</span>
          <span className={'pill' + (kbUp === false ? ' down' : '')}>
            {kbUp === null ? 'checking…' : kbUp ? 'knowledge base connected' : 'knowledge base down'}
          </span>
        </div>
      </aside>
      <div className="content">
        <main>
          {tab === 'chat' && <ChatPage />}
          {tab === 'documents' && <DocumentsPage />}
          {tab === 'prompts' && <PromptsPage />}
          {tab === 'registry' && <RegistryPage />}
          {tab === 'settings' && <SettingsPage onProviderChanged={refreshHeader} />}
        </main>
      </div>
    </div>
  );
}
