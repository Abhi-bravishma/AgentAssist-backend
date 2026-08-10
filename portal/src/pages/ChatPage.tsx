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
    <>
      <section className="panel">
        <h2>Test chat</h2>
        <p className="hint" style={{ marginTop: 0 }}>
          Talks to the real <code>/process</code> endpoint — language detection, intent, project
          gating, checklist and knowledge base included. Pick a project first: gating differs per
          project, and no selection means the backend default.
        </p>
        <div className="row">
          <label>Project{' '}
            <select value={project} onChange={e => setProject(e.target.value)}>
              <option value="">(backend default)</option>
              {projects.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <input type="text" placeholder="Mobile number (optional, for customer data)"
                 value={mobile} onChange={e => setMobile(e.target.value)} style={{ width: 260 }} />
          <span className="spacer" />
          <span className="hint">session: <code>{interactionId}</code></span>
          <button className="ghost btn" onClick={newSession}>New session</button>
        </div>
      </section>

      <section className="panel">
        <div style={{ maxHeight: 480, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10 }}>
          {entries.length === 0 && (
            <div className="hint">No messages yet. Send one as the customer to see the full pipeline output.</div>
          )}
          {entries.map(e => (
            <div key={e.id}>
              <div style={{
                display: 'flex',
                justifyContent: e.from === 'customer' ? 'flex-start' : 'flex-end',
              }}>
                <div style={{
                  background: 'var(--bg)', borderRadius: 10, padding: '8px 12px', maxWidth: '75%',
                }}>
                  <div className="hint" style={{ marginBottom: 2 }}>
                    {e.from === 'customer' ? 'Customer' : 'Agent'}
                  </div>
                  {e.text}
                </div>
              </div>
              {e.pending && <div className="hint" style={{ marginTop: 4 }}>processing…</div>}
              {e.error && <div className="hint" style={{ marginTop: 4, color: '#c00' }}>Error: {e.error}</div>}
              {e.result && <ResultCard r={e.result} />}
            </div>
          ))}
          <div ref={bottom} />
        </div>

        <div className="row" style={{ marginTop: 12 }}>
          <textarea rows={2} placeholder="Type a message… (Enter to send as customer)"
                    value={text}
                    onChange={e => setText(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send('customer'); }
                    }}
                    style={{ flex: 1 }} />
          <button className="btn" onClick={() => send('customer')} disabled={busy || !text.trim()}>
            {busy ? 'Processing…' : 'Send as customer'}
          </button>
          <button className="ghost btn" onClick={() => send('user')} disabled={busy || !text.trim()}>
            Send as agent
          </button>
        </div>
      </section>
    </>
  );
}

function ResultCard({ r }: { r: ProcessResponse }) {
  const [open, setOpen] = useState(false);
  return (
    <div style={{
      border: '1px solid var(--border, #ddd)', borderRadius: 10,
      padding: '10px 12px', margin: '6px 0 0 24px',
    }}>
      <div className="row" style={{ flexWrap: 'wrap', gap: 6 }}>
        <span className="pill">sentiment {r.currentSentiment}</span>
        <span className="pill">overall {r.overallSentiment}</span>
        {r.checklistOperation && <span className="tag">intent: {r.checklistOperation}</span>}
        {r.usedChecklist && <span className="tag">customer data</span>}
        {r.usedKnowledgeBase
          ? <span className="tag">knowledge base ({r.documentsFound} docs)</span>
          : <span className="hint">no KB match</span>}
        {r.customerName && <span className="tag">{r.customerName}</span>}
      </div>

      {r.suggestedResponses.map((s, i) => (
        <div key={i} style={{ marginTop: 8 }}>
          <div className="hint">Suggested reply {r.suggestedResponses.length > 1 ? i + 1 : ''}</div>
          <div>{s.englishReply}</div>
          {s.userLanguageReply && (
            <div style={{ marginTop: 2, fontStyle: 'italic' }}>{s.userLanguageReply}</div>
          )}
        </div>
      ))}

      {r.knowledgeSources.length > 0 && (
        <div className="row" style={{ marginTop: 8, flexWrap: 'wrap', gap: 6 }}>
          {r.knowledgeSources.map((k, i) => (
            <span key={i} className="tag" title={k.contentPreview || ''}>
              {k.fileName}{k.relevanceScore != null ? ` (${k.relevanceScore.toFixed(2)})` : ''}
            </span>
          ))}
        </div>
      )}

      {r.summary && (
        <div style={{ marginTop: 8 }}>
          <button className="ghost btn" onClick={() => setOpen(!open)}>
            {open ? 'Hide summary' : 'Show summary'}
          </button>
          {open && <div className="hint" style={{ marginTop: 6 }}>{r.summary}</div>}
        </div>
      )}
    </div>
  );
}
