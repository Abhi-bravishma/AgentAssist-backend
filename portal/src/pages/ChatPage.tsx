import { useEffect, useRef, useState } from 'react';
import { chat, ProcessResponse, registry } from '../api';
import { toast } from '../toast';

/**
 * Test chat: exercise the real /process pipeline from the portal. Every send
 * hits the same endpoint Avaya does — language detection, intent, checklist,
 * RAG, the lot. "New session" mints a fresh interactionId so conversation
 * state (base language, checklist cache) starts clean.
 */

interface ChatEntry {
  id: number;
  from: 'customer' | 'user';
  text: string;
  pending?: boolean;
  error?: string;
  result?: ProcessResponse;
}

function newInteractionId(): string {
  return 'portal-chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6);
}

function sentimentFace(score: number): string {
  if (score >= 0.3) return '😊';
  if (score <= -0.3) return '😠';
  return '😐';
}

export default function ChatPage() {
  const [projects, setProjects] = useState<string[]>([]);
  const [project, setProject] = useState('');
  const [mobile, setMobile] = useState('');
  const [interactionId, setInteractionId] = useState(newInteractionId);
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const nextId = useRef(1);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    registry.projects()
      .then(p => setProjects(p.filter(x => x.active).map(x => x.code)))
      .catch(() => toast('Could not load projects for the selector', true));
  }, []);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' });
  }, [entries]);

  const newSession = () => {
    if (entries.length && !window.confirm('Start a new session? The current chat is cleared and a fresh interaction id is generated.')) return;
    setInteractionId(newInteractionId());
    setEntries([]);
    toast('New session started');
  };

  const send = async (from: 'customer' | 'user') => {
    const message = text.trim();
    if (!message || busy) return;
    setText('');
    const id = nextId.current++;
    setEntries(e => [...e, { id, from, text: message, pending: true }]);
    setBusy(true);
    try {
      const r = await chat.process({
        interactionId, from, message,
        projectName: project,
        mobileNumber: mobile.trim() || undefined,
      });
      setEntries(e => e.map(x => x.id === id ? { ...x, pending: false, result: r } : x));
    } catch (err: any) {
      setEntries(e => e.map(x => x.id === id ? { ...x, pending: false, error: err.message } : x));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="chat-shell">
      <div className="chat-toolbar">
        <label>Project
          <input type="text" list="chat-project-options" value={project}
                 placeholder="(default)" style={{ width: 150 }}
                 onChange={e => setProject(e.target.value)} />
          <datalist id="chat-project-options">
            {projects.map(p => <option key={p} value={p} />)}
          </datalist>
        </label>
        <label>Mobile
          <input type="text" value={mobile} placeholder="optional" style={{ width: 150 }}
                 onChange={e => setMobile(e.target.value)} />
        </label>
        <span className="spacer" />
        <span className="chat-session" title="interaction id">{interactionId}</span>
        <button className="ghost btn" onClick={newSession}>✨ New session</button>
      </div>

      <div className="chat-log">
        {entries.length === 0 && (
          <div className="chat-empty">
            <div className="big">💬</div>
            <div>Talk to the real pipeline — try <b>“I want to waive my annual fee”</b> on METRO,</div>
            <div className="hint" style={{ marginTop: 4 }}>
              or start in another language and switch to English mid-chat to watch the reply
              language stick. Type any project name to test an unregistered one.
            </div>
          </div>
        )}
        {entries.map(e => (
          <div key={e.id}>
            <div className={'msg-row ' + (e.from === 'customer' ? 'customer' : 'agent')}>
              <div className="avatar">{e.from === 'customer' ? '🙋' : '🎧'}</div>
              <div className="bubble">
                <div className="who">{e.from === 'customer' ? 'Customer' : 'Agent'}</div>
                {e.text}
              </div>
            </div>
            {e.pending && (
              <div className="ai-card" style={{ width: 'fit-content' }}>
                <span className="typing"><span /><span /><span /></span>
              </div>
            )}
            {e.error && <div className="err-bubble">⚠ {e.error}</div>}
            {e.result && <ResultCard r={e.result} />}
          </div>
        ))}
        <div ref={bottom} />
      </div>

      <div className="chat-composer">
        <textarea rows={1} placeholder="Type a message…  (Enter = send as customer)"
                  value={text}
                  onChange={e => setText(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send('customer'); }
                  }} />
        <button className="btn" onClick={() => send('customer')} disabled={busy || !text.trim()}>
          {busy ? '…' : 'Send 🙋'}
        </button>
        <button className="ghost btn" onClick={() => send('user')} disabled={busy || !text.trim()}
                title="Agent messages are saved but get no suggestions — same as production">
          as agent 🎧
        </button>
      </div>
    </div>
  );
}

function ResultCard({ r }: { r: ProcessResponse }) {
  const [open, setOpen] = useState(false);
  const copy = (s: string) => {
    navigator.clipboard.writeText(s).then(() => toast('Copied'));
  };
  return (
    <div className="ai-card">
      <div className="chips">
        <span className="tag" title="current message sentiment">
          {sentimentFace(r.currentSentiment)} {r.currentSentiment}
        </span>
        <span className="tag" title="overall conversation sentiment">
          Σ {r.overallSentiment}
        </span>
        {r.checklistOperation && <span className="pill warn">🎯 {r.checklistOperation}</span>}
        {r.usedChecklist && <span className="pill">🗂 customer data</span>}
        {r.usedKnowledgeBase && <span className="pill">📚 KB · {r.documentsFound}</span>}
        {r.customerName && <span className="tag">👤 {r.customerName}</span>}
      </div>

      {r.suggestedResponses.map((s, i) => (
        <div key={i} className="suggestion">
          <div className="s-label">
            💡 Suggested reply{r.suggestedResponses.length > 1 ? ' ' + (i + 1) : ''}
            <button className="link" onClick={() => copy(s.userLanguageReply || s.englishReply)}>copy</button>
          </div>
          <div className="s-en">{s.englishReply}</div>
          {s.userLanguageReply && <div className="s-user">🌐 {s.userLanguageReply}</div>}
        </div>
      ))}

      {r.knowledgeSources.length > 0 && (
        <div className="chips" style={{ marginTop: 8 }}>
          {r.knowledgeSources.map((k, i) => (
            <span key={i} className="tag" title={k.contentPreview || ''}>
              📄 {k.fileName}{k.relevanceScore != null ? ` · ${k.relevanceScore.toFixed(2)}` : ''}
            </span>
          ))}
        </div>
      )}

      {r.summary && (
        <div style={{ marginTop: 6 }}>
          <button className="link" onClick={() => setOpen(!open)}>
            {open ? 'hide summary' : 'summary…'}
          </button>
          {open && <div className="hint" style={{ marginTop: 4 }}>{r.summary}</div>}
        </div>
      )}
    </div>
  );
}
