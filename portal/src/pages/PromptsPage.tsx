import { useEffect, useState } from 'react';
import { admin, Intent, Project, registry, TemplateSummary } from '../api';
import { CATEGORY_ORDER, TEMPLATE_INFO, friendlyName, templateCategory } from '../templateInfo';
import { toast } from '../toast';

/** Set by the Setup page's "Create its checklist now" button. */
export const NEW_CHECKLIST_FLAG = 'aa-new-checklist-for';

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
  // "+ New checklist" form
  const [intents, setIntents] = useState<Intent[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [newOpen, setNewOpen] = useState(false);
  const [newIntent, setNewIntent] = useState('');
  const [newProject, setNewProject] = useState('');
  const [isDraftNew, setIsDraftNew] = useState(false);

  const load = async () => {
    try {
      const list = await admin.templates();
      setTemplates(list);
    } catch (e: any) {
      toast('Could not load the instruction list: ' + e.message, true);
    }
  };

  useEffect(() => {
    load();
    registry.intents().then(setIntents).catch(() => {});
    registry.projects().then(p => setProjects(p.filter(x => x.active))).catch(() => {});
    // Arrived here via Setup's "Create its checklist now"?
    const pending = sessionStorage.getItem(NEW_CHECKLIST_FLAG);
    if (pending) {
      sessionStorage.removeItem(NEW_CHECKLIST_FLAG);
      setNewOpen(true);
      setNewIntent(pending);
    }
  }, []);

  const dirty = selected !== null && content !== originalContent;

  const select = async (t: TemplateSummary) => {
    if (selected && variantId(selected) === variantId(t)) return;
    if (dirty && !window.confirm(
      'You have unpublished edits — switching will discard them.\n\nDiscard the edits?')) return;
    setIsDraftNew(false);
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

  /** Open the editor for a checklist that does not exist yet (v1 on publish). */
  const startNewChecklist = () => {
    if (!newIntent) return;
    const key = 'checklist.' + newIntent.toLowerCase();
    const projectCode = newProject || null;
    const existing = templates.find(t => t.templateKey === key
      && (t.projectCode ?? null) === projectCode);
    if (existing) {
      toast('That checklist already exists — opening it');
      setNewOpen(false);
      select(existing);
      return;
    }
    if (dirty && !window.confirm(
      'You have unpublished edits — switching will discard them.\n\nDiscard the edits?')) return;
    setIsDraftNew(true);
    setSelected({ templateKey: key, projectCode, publishedVersion: null, latestVersion: null, latestStatus: 'NEW' });
    setContent('');
    setOriginalContent('');
    setVersion(null);
    setNewOpen(false);
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
      setIsDraftNew(false);
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
            <button className="btn" onClick={() => setNewOpen(!newOpen)}>+ New checklist</button>
            <button className="ghost btn" onClick={load}>Refresh</button>
          </div>
          {newOpen && (
            <div style={{ marginBottom: 12, padding: '10px 12px', background: 'var(--bg)', borderRadius: 8 }}>
              <p className="hint" style={{ marginTop: 0 }}>
                A checklist is the step-by-step guidance the AI follows for one request type.
                Pick the request type (create it on the Setup page first if it doesn’t exist),
                choose who it applies to, then write and publish the text.
              </p>
              <div className="row">
                <div>
                  <label>Request type</label>
                  <select value={newIntent} onChange={e => setNewIntent(e.target.value)}>
                    <option value="">— choose —</option>
                    {intents.filter(i => i.active).map(i => (
                      <option key={i.code} value={i.code}>{i.displayName || i.code}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label>Applies to</label>
                  <select value={newProject} onChange={e => setNewProject(e.target.value)}>
                    <option value="">All projects</option>
                    {projects.map(p => <option key={p.code} value={p.code}>{p.code} only</option>)}
                  </select>
                </div>
                <button className="btn" onClick={startNewChecklist} disabled={!newIntent}>
                  Write the checklist →
                </button>
                <button className="ghost btn" onClick={() => setNewOpen(false)}>Cancel</button>
              </div>
            </div>
          )}
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
                {isDraftNew && <> · <span className="pill warn">NEW — doesn’t exist until you publish</span></>}
                {version != null && <> · Live version: <b>{version}</b></>}
                {dirty
                  ? <> · publishing creates version {(version ?? 0) + 1}</>
                  : <> · nothing to publish yet — {isDraftNew ? 'write the checklist first' : 'edit the text first'}</>}
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
                : <textarea value={content} onChange={e => setContent(e.target.value)} spellCheck={false}
                            placeholder={isDraftNew
                              ? 'Write the step-by-step guidance the AI should follow for this request type.\n\n'
                                + 'Example structure:\n'
                                + 'CHECKLIST — <what this handles>:\n'
                                + '1. Verify the customer’s identity.\n'
                                + '2. Ask which product/service the request is about.\n'
                                + '3. Explain the rules: <your business rules here>.\n'
                                + '4. If not resolvable, direct the customer to {hotline}.\n\n'
                                + 'You can use slots like {bank_name} and {hotline} from Setup → Company details.'
                              : undefined} />}
            </>
          )}
        </section>
      </div>
    </>
  );
}
