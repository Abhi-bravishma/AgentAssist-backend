import { useEffect, useState } from 'react';
import { admin, TemplateSummary } from '../api';
import { CATEGORY_ORDER, TEMPLATE_INFO, friendlyName, templateCategory } from '../templateInfo';
import { toast } from '../toast';

/** Key for a variant row: "checklist.fee_waiver" or "checklist.fee_waiver@SCB". */
const variantId = (t: { templateKey: string; projectCode: string | null }) =>
  t.templateKey + (t.projectCode ? '@' + t.projectCode : '');

const appliesTo = (t: TemplateSummary) =>
  t.projectCode ? `${t.projectCode} only` : 'All projects';

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
      toast('Could not load the instruction list: ' + e.message, true);
    }
  };

  useEffect(() => { load(); }, []);

  const dirty = selected !== null && content !== originalContent;

  const select = async (t: TemplateSummary) => {
    if (selected && variantId(selected) === variantId(t)) return;
    if (dirty && !window.confirm(
      'You have unpublished edits — switching will discard them.\n\nDiscard the edits?')) return;
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
      toast('Could not load the text: ' + e.message, true);
      setSelected(null);
    } finally {
      setLoading(false);
    }
  };

  const publish = async () => {
    if (!selected || !dirty) return;
    const scope = selected.projectCode
      ? `for ${selected.projectCode} only`
      : 'for every project without its own copy';
    if (!window.confirm(
      `Make this change live?\n\nFrom this moment the AI uses your new text ` +
      `in all conversations ${scope}. Your previous version is kept and can be ` +
      `restored by publishing its text again.`)) return;
    setPublishing(true);
    try {
      const r = await admin.publishTemplate(selected.templateKey, selected.projectCode, content);
      toast(`"${friendlyName(r.templateKey)}" is now live (version ${r.version})`);
      setOriginalContent(content);
      setVersion(r.version);
      await load();
    } catch (e: any) {
      toast('Could not publish: ' + e.message, true);
    } finally {
      setPublishing(false);
    }
  };

  // Group rows by category, keeping a template's default and project copies together
  const grouped = CATEGORY_ORDER
    .map(cat => ({
      cat,
      rows: templates
        .filter(t => templateCategory(t.templateKey) === cat)
        .sort((a, b) => a.templateKey === b.templateKey
          ? (a.projectCode ?? '').localeCompare(b.projectCode ?? '')
          : a.templateKey.localeCompare(b.templateKey)),
    }))
    .filter(g => g.rows.length > 0);

  return (
    <>
      <section className="panel">
        <h2 style={{ marginBottom: 6 }}>AI instructions</h2>
        <p className="hint" style={{ margin: 0 }}>
          These are the written instructions our AI follows when helping agents. Pick one on the
          left, edit the text on the right, then press <b>Make changes live</b>. Words in curly
          braces like <code>{'{bank_name}'}</code> are fill-in-the-blank slots — the system replaces
          them with real values (managed on the Setup page) each time the AI runs. If a project has
          its own copy of an instruction, that copy is used for that project; otherwise the
          “All projects” version applies.
        </p>
      </section>

      <div className="grid2">
        <section className="panel">
          <div className="row" style={{ marginBottom: 10 }}>
            <h2 style={{ margin: 0 }}>Instructions</h2>
            <span className="spacer" />
            <button className="ghost btn" onClick={load}>Refresh</button>
          </div>
          <div className="scroll">
            <table>
              <thead>
                <tr>
                  <th>Instruction</th>
                  <th title="A project-specific copy replaces the default for that project.">Applies to</th>
                  <th title="The version the AI is using right now. Publishing raises it by one.">Live version</th>
                </tr>
              </thead>
              <tbody>
                {grouped.map(g => (
                  <>
                    <tr key={'cat-' + g.cat}>
                      <td colSpan={3} style={{ fontWeight: 600, fontSize: 12, color: 'var(--muted)', paddingTop: 14 }}>
                        {g.cat}
                      </td>
                    </tr>
                    {g.rows.map(t => (
                      <tr key={variantId(t)}
                          className={'selectable' + (selected && variantId(selected) === variantId(t) ? ' selected' : '')}
                          onClick={() => select(t)}>
                        <td className="name">
                          {friendlyName(t.templateKey)}
                          <div className="hint" style={{ fontWeight: 400 }}>{t.templateKey}</div>
                        </td>
                        <td>{t.projectCode
                          ? <span className="tag">{t.projectCode} only</span>
                          : <span className="hint">All projects</span>}</td>
                        <td>{t.publishedVersion != null ? 'v' + t.publishedVersion : <span className="hint">not live yet</span>}</td>
                      </tr>
                    ))}
                  </>
                ))}
              </tbody>
            </table>
          </div>
          {templates.length === 0 && (
            <div className="empty">
              No AI instructions found — they are seeded by the technical team.
            </div>
          )}
        </section>

        <section className="panel">
          {!selected ? (
            <div className="empty">Select an instruction on the left to read or edit it.</div>
          ) : (
            <>
              <div className="row" style={{ marginBottom: 6 }}>
                <h2 style={{ margin: 0 }}>{friendlyName(selected.templateKey)}</h2>
                <span className="spacer" />
                <button className="btn" onClick={publish} disabled={!dirty || publishing || loading}>
                  {publishing ? 'Publishing…' : 'Make changes live'}
                </button>
              </div>
              <p className="hint" style={{ marginTop: 0, marginBottom: 6 }}>
                For: <b>{appliesTo(selected)}</b>
                {version != null && <> · Live version: <b>{version}</b></>}
                {dirty
                  ? <> · publishing creates version {(version ?? 0) + 1}</>
                  : <> · nothing to publish yet — edit the text first</>}
              </p>
              {TEMPLATE_INFO[selected.templateKey] && (
                <p className="hint" style={{ marginTop: 0 }}>
                  {TEMPLATE_INFO[selected.templateKey].what}
                </p>
              )}
              <p className="hint" style={{ marginTop: 0 }}>
                Typing here changes nothing until you press the button. Keep the curly-brace slots
                — you can move them around, but don’t delete or rename them.
              </p>
              {loading
                ? <div className="empty">Loading…</div>
                : <textarea value={content} onChange={e => setContent(e.target.value)} spellCheck={false} />}
            </>
          )}
        </section>
      </div>
    </>
  );
}
