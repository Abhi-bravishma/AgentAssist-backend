import { useEffect, useState } from 'react';
import { admin, TemplateSummary } from '../api';
import { toast } from '../toast';

/** Key for a variant row: "checklist.fee_waiver" or "checklist.fee_waiver@SCB". */
const variantId = (t: { templateKey: string; projectCode: string | null }) =>
  t.templateKey + (t.projectCode ? '@' + t.projectCode : '');

export default function PromptsPage() {
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [selected, setSelected] = useState<TemplateSummary | null>(null);
  const [content, setContent] = useState('');
  const [originalContent, setOriginalContent] = useState('');
  const [version, setVersion] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);

  const load = async () => {
    try {
      const list = await admin.templates();
      setTemplates(list);
    } catch (e: any) {
      toast('Could not load templates: ' + e.message, true);
    }
  };

  useEffect(() => { load(); }, []);

  const select = async (t: TemplateSummary) => {
    setSelected(t);
    setLoading(true);
    setContent('');
    setVersion(null);
    try {
      const c = await admin.templateContent(t.templateKey, t.projectCode);
      setContent(c.content);
      setOriginalContent(c.content);
      setVersion(c.version);
    } catch (e: any) {
      toast('Could not load content: ' + e.message, true);
      setSelected(null);
    } finally {
      setLoading(false);
    }
  };

  const dirty = selected !== null && content !== originalContent;

  const publish = async () => {
    if (!selected || !dirty) return;
    if (!window.confirm(
      `Publish a new version of "${selected.templateKey}"` +
      (selected.projectCode ? ` (project ${selected.projectCode})` : ' (default)') +
      `?\n\nIt goes LIVE immediately for every conversation. The current v${version} stays in history.`)) return;
    setPublishing(true);
    try {
      const r = await admin.publishTemplate(selected.templateKey, selected.projectCode, content);
      toast(`Published ${r.templateKey} v${r.version} — live now`);
      setOriginalContent(content);
      setVersion(r.version);
      await load();
    } catch (e: any) {
      toast('Publish failed: ' + e.message, true);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="grid2">
      <section className="panel">
        <div className="row" style={{ marginBottom: 10 }}>
          <h2 style={{ margin: 0 }}>Prompt templates</h2>
          <span className="spacer" />
          <button className="ghost btn" onClick={load}>Refresh</button>
        </div>
        <div className="scroll">
          <table>
            <thead>
              <tr><th>Key</th><th>Variant</th><th>Published</th></tr>
            </thead>
            <tbody>
              {templates.map(t => (
                <tr key={variantId(t)}
                    className={'selectable' + (selected && variantId(selected) === variantId(t) ? ' selected' : '')}
                    onClick={() => select(t)}>
                  <td className="name">{t.templateKey}</td>
                  <td>{t.projectCode ? <span className="tag">{t.projectCode}</span> : <span className="hint">default</span>}</td>
                  <td>{t.publishedVersion != null ? 'v' + t.publishedVersion : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {templates.length === 0 && <div className="empty">No templates.</div>}
      </section>

      <section className="panel">
        {!selected ? (
          <div className="empty">Select a template to view and edit it.</div>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 10 }}>
              <h2 style={{ margin: 0 }}>
                {selected.templateKey}
                {selected.projectCode ? ` — ${selected.projectCode}` : ' — default'}
                {version != null && <span className="hint">{'  '}v{version}</span>}
              </h2>
              <span className="spacer" />
              <button className="btn" onClick={publish} disabled={!dirty || publishing || loading}>
                {publishing ? 'Publishing…' : dirty ? `Publish as v${(version ?? 0) + 1}` : 'No changes'}
              </button>
            </div>
            <p className="hint" style={{ marginTop: 0 }}>
              Placeholders like {'{bank_name}'} are substituted at runtime — keep them intact.
              Publishing inserts a new version and is live immediately; earlier versions stay in
              the database as history.
            </p>
            {loading
              ? <div className="empty">Loading…</div>
              : <textarea value={content} onChange={e => setContent(e.target.value)} spellCheck={false} />}
          </>
        )}
      </section>
    </div>
  );
}
