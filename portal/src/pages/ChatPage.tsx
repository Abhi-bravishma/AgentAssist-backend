import { useEffect, useRef, useState } from 'react';
import { chat, KnowledgeSource, registry, SuggestedPiece } from '../api';
import { toast } from '../toast';

/**
 * Test chat, piece by piece.
 *
 * A message is submitted once (POST /process/async) and then each piece is
 * shown the moment it exists: sentiment, the conversation summary and the
 * suggested reply each arrive over their own SSE stream — the reply token by
 * token, as the model writes it. Nothing waits for anything else, which is the
 * whole point: the agent sees whatever is ready, not a blank card until the
 * slowest part finishes.
 */

interface Live {
  processId: string;
  sentiment?: { overallSentiment: number; currentSentiment: number; label?: string };
  summary?: string;
  /** Streamed summary text so far. */
  summaryTokens: string;
  /** Streamed reply text so far. */
  tokens: string;
  streaming: boolean;
  suggestions?: SuggestedPiece['suggestedResponses'];
  sources?: KnowledgeSource[];
  documentsFound?: number;
  usedKnowledgeBase?: boolean;
  error?: string;
  sentimentDone: boolean;
  summaryDone: boolean;
  suggestedDone: boolean;
  /** performance.now() stamps: when the send was clicked and when each piece arrived. */
  startedAt: number;
  sentimentAt?: number;
  summaryFirstAt?: number;
  summaryAt?: number;
  replyFirstAt?: number;
  replyAt?: number;
  finishedAt?: number;
}

interface ChatEntry {
  id: number;
  from: 'customer' | 'user';
  text: string;
  live?: Live;
  error?: string;
}

function newInteractionId(): string {
  return 'portal-chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6);
}

function sentimentFace(score: number): string {
  if (score >= 0.3) return '😊';
  if (score <= -0.3) return '😠';
  return '😐';
}

/** The model writes a numbered list; the leading "1. " is noise while streaming. */
function cleanTokens(s: string): string {
  return s.replace(/^\s*\d+[.)]\s*/, '');
}

const emptyLive = (processId: string, startedAt: number): Live => ({
  processId, tokens: '', summaryTokens: '', streaming: false,
  sentimentDone: false, summaryDone: false, suggestedDone: false,
  startedAt,
});

const secs = (from: number, to: number) => ((to - from) / 1000).toFixed(1) + 's';

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
  const streams = useRef<EventSource[]>([]);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    registry.projects()
      .then(p => setProjects(p.filter(x => x.active).map(x => x.code)))
      .catch(() => toast('Could not load projects for the selector', true));
    return () => {
      alive.current = false;
      streams.current.forEach(s => s.close());
    };
  }, []);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' });
  }, [entries]);

  const [, tick] = useState(0);
  const anyRunning = entries.some(e => e.live && !e.live.finishedAt && !e.live.error);
  useEffect(() => {
    if (!anyRunning) return;
    const id = setInterval(() => tick(n => n + 1), 100);
    return () => clearInterval(id);
  }, [anyRunning]);

  const newSession = () => {
    if (entries.length && !window.confirm('Start a new session? The current chat is cleared and a fresh interaction id is generated.')) return;
    streams.current.forEach(s => s.close());
    streams.current = [];
    setInteractionId(newInteractionId());
    setEntries([]);
    setBusy(false);
    toast('New session started');
  };

  /** Patch one entry's live state; releases the composer once every piece is in. */
  const patch = (id: number, fn: (l: Live) => Live) => {
    setEntries(e => e.map(x => {
      if (x.id !== id || !x.live) return x;
      const next = fn(x.live);
      if ((next.sentimentDone && next.summaryDone && next.suggestedDone) || next.error) {
        if (!next.finishedAt) next.finishedAt = performance.now();
        setTimeout(() => setBusy(false), 0);
      }
      return { ...x, live: next };
    }));
  };

  const watch = (id: number, processId: string) => {
    // --- conversation summary: its own stream ---
    const summary = new EventSource(chat.summarySseUrl(processId));
    streams.current.push(summary);
    summary.addEventListener('token', ev => {
      const p = JSON.parse((ev as MessageEvent).data);
      patch(id, l => ({ ...l, summaryTokens: l.summaryTokens + p.text, summaryFirstAt: l.summaryFirstAt ?? performance.now() }));
    });
    summary.addEventListener('summary', ev => {
      const p = JSON.parse((ev as MessageEvent).data);
      patch(id, l => ({ ...l, summary: p.summary, summaryAt: l.summaryAt ?? performance.now() }));
    });
    summary.addEventListener('done', () => {
      summary.close();
      patch(id, l => ({ ...l, summaryDone: true }));
    });
    summary.addEventListener('error', ev => {
      const data = (ev as MessageEvent).data;
      if (data) { summary.close(); patch(id, l => ({ ...l, error: JSON.parse(data).error, summaryDone: true })); }
      // otherwise: connection hiccup — EventSource reconnects on its own
    });

    // --- suggested reply: its own stream, token by token ---
    const suggested = new EventSource(chat.suggestedSseUrl(processId));
    streams.current.push(suggested);
    suggested.addEventListener('token', ev => {
      const p = JSON.parse((ev as MessageEvent).data);
      patch(id, l => ({ ...l, tokens: l.tokens + p.text, streaming: true, replyFirstAt: l.replyFirstAt ?? performance.now() }));
    });
    suggested.addEventListener('suggestions', ev => {
      const p: SuggestedPiece = JSON.parse((ev as MessageEvent).data);
      patch(id, l => ({
        ...l, streaming: false, replyAt: l.replyAt ?? performance.now(),
        suggestions: p.suggestedResponses ?? [],
        sources: p.knowledgeSources ?? [],
        documentsFound: p.documentsFound,
        usedKnowledgeBase: p.usedKnowledgeBase,
      }));
    });
    suggested.addEventListener('done', () => {
      suggested.close();
      patch(id, l => ({ ...l, streaming: false, suggestedDone: true }));
    });
    suggested.addEventListener('error', ev => {
      const data = (ev as MessageEvent).data;
      if (data) { suggested.close(); patch(id, l => ({ ...l, error: JSON.parse(data).error, suggestedDone: true, streaming: false })); }
    });

    // --- sentiment: its own stream, same shape as the other two ---
    const sentiment = new EventSource(chat.sentimentSseUrl(processId));
    streams.current.push(sentiment);
    sentiment.addEventListener('sentiment', ev => {
      const p = JSON.parse((ev as MessageEvent).data);
      patch(id, l => ({ ...l, sentiment: p, sentimentAt: l.sentimentAt ?? performance.now() }));
    });
    sentiment.addEventListener('done', () => {
      sentiment.close();
      patch(id, l => ({ ...l, sentimentDone: true }));
    });
    sentiment.addEventListener('error', ev => {
      const data = (ev as MessageEvent).data;
      if (data) { sentiment.close(); patch(id, l => ({ ...l, error: JSON.parse(data).error, sentimentDone: true })); }
    });
  };

  const send = async (from: 'customer' | 'user') => {
    const message = text.trim();
    if (!message || busy) return;
    setText('');
    const id = nextId.current++;
    setEntries(e => [...e, { id, from, text: message }]);
    setBusy(true);
    const startedAt = performance.now();
    try {
      const { processId } = await chat.processAsync({
        interactionId, from, message,
        projectName: project,
        mobileNumber: mobile.trim() || undefined,
      });
      setEntries(e => e.map(x => x.id === id ? { ...x, live: emptyLive(processId, startedAt) } : x));
      watch(id, processId);
    } catch (err: any) {
      setEntries(e => e.map(x => x.id === id ? { ...x, error: err.message } : x));
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
              language stick. Each piece appears the moment it is ready.
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
            {!e.live && !e.error && (
              <div className="ai-card" style={{ width: 'fit-content' }}>
                <span className="typing"><span /><span /><span /></span>
              </div>
            )}
            {e.error && <div className="err-bubble">⚠ {e.error}</div>}
            {e.live && <LiveCard live={e.live} isAgent={e.from === 'user'} />}
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

function LiveCard({ live, isAgent }: { live: Live; isAgent: boolean }) {
  const copy = (s: string) => {
    navigator.clipboard.writeText(s).then(() => toast('Copied'));
  };
  const streamed = cleanTokens(live.tokens);

  const now = performance.now();
  const running = !live.finishedAt && !live.error;
  const at = (t?: number) => t ? secs(live.startedAt, t) : null;

  return (
    <div className="ai-card">
      <div className="timing" title="measured in the browser from the moment you pressed send">
        <span className={'t-total' + (running ? ' running' : '')}>
          ⏱ {secs(live.startedAt, live.finishedAt ?? now)}{running ? '' : ' total'}
        </span>
        <span className="t-piece">sentiment <b>{at(live.sentimentAt) ?? '…'}</b></span>
        <span className="t-piece">summary <b>{at(live.summaryAt) ?? '…'}</b>{live.summaryFirstAt && <i> (first word {at(live.summaryFirstAt)})</i>}</span>
        <span className="t-piece">reply <b>{at(live.replyAt) ?? '…'}</b>{live.replyFirstAt && <i> (first word {at(live.replyFirstAt)})</i>}</span>
      </div>
      <div className="chips">
        {live.sentiment ? (
          <>
            <span className="tag" title="current message sentiment">
              {sentimentFace(live.sentiment.currentSentiment)} {live.sentiment.currentSentiment}
            </span>
            <span className="tag" title="overall conversation sentiment">
              Σ {live.sentiment.overallSentiment}
            </span>
          </>
        ) : (
          <span className="live-pending">analysing sentiment…</span>
        )}
        {live.usedKnowledgeBase && <span className="pill">📚 KB · {live.documentsFound}</span>}
      </div>

      <div className="piece-label">Suggested reply</div>
      {live.suggestions ? (
        live.suggestions.length === 0 ? (
          <div className="hint">{isAgent ? 'No suggestions for agent messages.' : 'No suggestion produced.'}</div>
        ) : live.suggestions.map((s, i) => (
          <div key={i} className="suggestion">
            <div className="s-label">
              💡 Suggested reply{live.suggestions!.length > 1 ? ' ' + (i + 1) : ''}
              <button className="link" onClick={() => copy(s.userLanguageReply || s.englishReply)}>copy</button>
            </div>
            <div className="s-en">{s.englishReply}</div>
            {s.userLanguageReply && <div className="s-user">🌐 {s.userLanguageReply}</div>}
          </div>
        ))
      ) : streamed ? (
        <div className="suggestion">
          <div className={'s-en stream-text' + (live.streaming ? ' streaming' : '')}>{streamed}</div>
        </div>
      ) : (
        <div className="live-pending">
          {live.suggestedDone ? 'no reply for this message' : 'searching the knowledge base…'}
        </div>
      )}

      {live.sources && live.sources.length > 0 && (
        <div className="chips" style={{ marginTop: 8 }}>
          {live.sources.map((k, i) => (
            <span key={i} className="tag" title={k.contentPreview || ''}>
              📄 {k.fileName}{k.relevanceScore != null ? ` · ${k.relevanceScore.toFixed(2)}` : ''}
            </span>
          ))}
        </div>
      )}

      <div className="piece-label">Conversation summary</div>
      {live.summary != null
        ? <div className="hint">{live.summary || '(empty)'}</div>
        : live.summaryTokens
          ? <div className="hint stream-text streaming">{live.summaryTokens}</div>
          : <div className="live-pending">summarising…</div>}

      {live.error && <div className="err-bubble" style={{ marginTop: 8 }}>⚠ {live.error}</div>}
    </div>
  );
}
