import { useEffect, useState } from 'react';
import { admin, kb } from './api';
import DocumentsPage from './pages/DocumentsPage';
import PromptsPage from './pages/PromptsPage';
import SettingsPage from './pages/SettingsPage';

type Tab = 'documents' | 'prompts' | 'settings';

export default function App() {
  const [tab, setTab] = useState<Tab>(
    (window.location.hash.replace('#', '') as Tab) || 'documents',
  );
  const [kbUp, setKbUp] = useState<boolean | null>(null);
  const [provider, setProvider] = useState<string>('…');

  useEffect(() => {
    window.location.hash = tab;
  }, [tab]);

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

  return (
    <>
      <header>
        <h1>Agent Assist — Portal</h1>
        <nav>
          {(['documents', 'prompts', 'settings'] as Tab[]).map(t => (
            <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
              {t[0].toUpperCase() + t.slice(1)}
            </button>
          ))}
        </nav>
        <span className="spacer" />
        <span className="pill" style={{ background: 'var(--bg)', color: 'var(--muted)' }}>
          AI: {provider}
        </span>
        <span className={'pill' + (kbUp === false ? ' down' : '')}>
          {kbUp === null ? 'checking…' : kbUp ? 'knowledge base connected' : 'knowledge base down'}
        </span>
      </header>
      <main>
        {tab === 'documents' && <DocumentsPage />}
        {tab === 'prompts' && <PromptsPage />}
        {tab === 'settings' && <SettingsPage onProviderChanged={refreshHeader} />}
      </main>
    </>
  );
}
