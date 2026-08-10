import { useEffect, useState } from 'react';
import { BrandAttribute, Intent, Project, registry } from '../api';
import { toast } from '../toast';

/**
 * Setup page: request types (intents), projects and their allowed request
 * types, and company/brand details. Everything here is live configuration —
 * a save takes effect in real conversations immediately.
 */
export default function RegistryPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [intents, setIntents] = useState<Intent[]>([]);
  const [brands, setBrands] = useState<BrandAttribute[]>([]);

  const reload = async () => {
    try {
      const [p, i, b] = await Promise.all([
        registry.projects(), registry.intents(), registry.brandAttributes(),
      ]);
      setProjects(p);
      setIntents(i);
      setBrands(b);
    } catch (e: any) {
      toast('Could not load setup data: ' + e.message, true);
    }
  };

  useEffect(() => { reload(); }, []);

  return (
    <>
      <section className="panel">
        <h2 style={{ marginBottom: 6 }}>Setup</h2>
        <p className="hint" style={{ margin: 0 }}>
          <b>1 · Request types</b> — the things customers ask for that the AI specially recognizes
          (fee waiver, billing…).{' '}
          <b>2 · Projects</b> — the clients/brands this assistant works for; each project chooses
          which request types it handles.{' '}
          <b>3 · Company details</b> — names and numbers (bank name, hotline…) that fill the
          curly-brace slots in the AI instructions.{' '}
          <b>Every save takes effect immediately in live conversations.</b>
        </p>
      </section>
      <IntentsSection intents={intents} reload={reload} />
      <ProjectsSection projects={projects} intents={intents} reload={reload} />
      <BrandsSection brands={brands} projects={projects} reload={reload} />
    </>
  );
}

const preview = (s: string | null, n = 70) =>
  !s ? null : s.length > n ? s.slice(0, n) + '…' : s;

// ==================== 1. request types (intents) ====================

function IntentsSection({ intents, reload }:
    { intents: Intent[]; reload: () => Promise<void> }) {
  const [selected, setSelected] = useState<string | null>(null);
  const [form, setForm] = useState({ displayName: '', description: '', filteredMessageTemplate: '' });
  const [creating, setCreating] = useState(false);
  const [create, setCreate] = useState({ code: '', displayName: '', description: '', filteredMessageTemplate: '' });
  const [busy, setBusy] = useState(false);

  const current = intents.find(i => i.code === selected) || null;

  const select = (i: Intent) => {
    if (selected === i.code) { setSelected(null); return; }
    setSelected(i.code);
    setForm({
      displayName: i.displayName || '',
      description: i.description || '',
      filteredMessageTemplate: i.filteredMessageTemplate || '',
    });
  };

  const save = async () => {
    if (!current) return;
    setBusy(true);
    try {
      await registry.updateIntent(current.code, form);
      toast(`"${current.displayName || current.code}" saved — the AI uses the new rule from the next message on`);
      await reload();
    } catch (e: any) {
      toast('Could not save: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (i: Intent) => {
    const msg = i.active
      ? `Switch off "${i.displayName || i.code}"?\n\nThe AI immediately stops recognizing this request type for ALL projects. Conversations about it will be treated as general questions.`
      : `Switch "${i.displayName || i.code}" back on?\n\nThe AI starts recognizing it again immediately.`;
    if (!window.confirm(msg)) return;
    try {
      await registry.updateIntent(i.code, { active: !i.active });
      toast(`"${i.displayName || i.code}" is now ${i.active ? 'off' : 'on'}`);
      await reload();
    } catch (e: any) {
      toast('Could not update: ' + e.message, true);
    }
  };

  const doCreate = async () => {
    setBusy(true);
    try {
      const made = await registry.createIntent(create);
      toast(`"${made.displayName || made.code}" created — the AI recognizes it from the next message on. `
        + `Optional: give it a checklist by publishing "checklist.${made.code.toLowerCase()}" on the AI instructions page.`);
      setCreate({ code: '', displayName: '', description: '', filteredMessageTemplate: '' });
      setCreating(false);
      await reload();
    } catch (e: any) {
      toast('Could not create: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2>1 · Request types</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        A request type is something a customer asks for that gets special handling. The{' '}
        <b>recognition rule</b> is literally the sentence you tell the AI about when to pick it —
        e.g. <i>Return "ROOM_BOOKING" if the customer asks to book a room.</i> The{' '}
        <b>“not available” message</b> is what the agent sees when a project does not offer that
        request type (<code>{'{project}'}</code> becomes the project name).
      </p>
      <table>
        <thead>
          <tr>
            <th>Request type</th>
            <th>Recognition rule (what tells the AI to pick it)</th>
            <th>“Not available” message</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {intents.map(i => (
            <tr key={i.code} className={'selectable' + (selected === i.code ? ' selected' : '')}
                onClick={() => select(i)}>
              <td className="name">
                {i.displayName || i.code}
                <div className="hint" style={{ fontWeight: 400 }}>{i.code}</div>
              </td>
              <td>{i.description
                ? <span className="hint">{preview(i.description)}</span>
                : <span className="pill down">missing — the AI will never pick this type</span>}</td>
              <td>{i.filteredMessageTemplate
                ? <span className="hint">{preview(i.filteredMessageTemplate, 50)}</span>
                : <span className="hint">—</span>}</td>
              <td><span className={'pill' + (i.active ? '' : ' down')}>{i.active ? 'on' : 'off'}</span></td>
              <td onClick={e => e.stopPropagation()}>
                <button className="ghost btn" onClick={() => toggleActive(i)}>
                  {i.active ? 'Switch off' : 'Switch on'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {current && (
        <div style={{ marginTop: 14 }}>
          <h3 style={{ margin: '0 0 8px' }}>Edit “{current.displayName || current.code}”</h3>
          <label>Display name</label>
          <input type="text" value={form.displayName}
                 onChange={e => setForm({ ...form, displayName: e.target.value })}
                 style={{ width: 260 }} />
          <label style={{ marginTop: 8 }}>Recognition rule — the sentence that teaches the AI when to pick this type (required)</label>
          <textarea rows={4}
                    placeholder='e.g. Return "ROOM_BOOKING" if the customer asks to book, change or cancel a room reservation.'
                    value={form.description}
                    onChange={e => setForm({ ...form, description: e.target.value })}
                    style={{ width: '100%' }} />
          <label style={{ marginTop: 8 }}>“Not available” message — shown when a project doesn’t offer this ({'{project}'} becomes the project name)</label>
          <textarea rows={2}
                    placeholder="e.g. Room booking is not available through {project}."
                    value={form.filteredMessageTemplate}
                    onChange={e => setForm({ ...form, filteredMessageTemplate: e.target.value })}
                    style={{ width: '100%' }} />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={save} disabled={busy || !form.description.trim()}>
              {busy ? 'Saving…' : 'Save — live immediately'}
            </button>
          </div>
        </div>
      )}

      {creating ? (
        <div style={{ marginTop: 14 }}>
          <h3 style={{ margin: '0 0 8px' }}>Add a request type</h3>
          <div className="row">
            <div>
              <label>Code — CAPITALS_WITH_UNDERSCORES, permanent once created</label>
              <input type="text" placeholder="e.g. ROOM_BOOKING" value={create.code}
                     onChange={e => setCreate({ ...create, code: e.target.value })} style={{ width: 220 }} />
            </div>
            <div>
              <label>Display name</label>
              <input type="text" placeholder="e.g. Room Booking" value={create.displayName}
                     onChange={e => setCreate({ ...create, displayName: e.target.value })} style={{ width: 240 }} />
            </div>
          </div>
          <label style={{ marginTop: 8 }}>Recognition rule (required)</label>
          <textarea rows={4}
                    placeholder='e.g. Return "ROOM_BOOKING" if the customer asks to book, change or cancel a room reservation.'
                    value={create.description}
                    onChange={e => setCreate({ ...create, description: e.target.value })}
                    style={{ width: '100%' }} />
          <label style={{ marginTop: 8 }}>“Not available” message (optional, {'{project}'} becomes the project name)</label>
          <textarea rows={2}
                    placeholder="e.g. Room booking is not available through {project}."
                    value={create.filteredMessageTemplate}
                    onChange={e => setCreate({ ...create, filteredMessageTemplate: e.target.value })}
                    style={{ width: '100%' }} />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={doCreate}
                    disabled={busy || !create.code.trim() || !create.description.trim()}>
              {busy ? 'Creating…' : 'Create — the AI starts recognizing it immediately'}
            </button>
            <button className="ghost btn" onClick={() => setCreating(false)}>Cancel</button>
          </div>
        </div>
      ) : (
        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn" onClick={() => setCreating(true)}>Add a request type</button>
          <span className="hint">
            New request types work for unrestricted projects right away; restricted projects must
            enable them below.
          </span>
        </div>
      )}
    </section>
  );
}

// ==================== 2. projects + allowed request types ====================

function ProjectsSection({ projects, intents, reload }:
    { projects: Project[]; intents: Intent[]; reload: () => Promise<void> }) {
  const [selected, setSelected] = useState<string | null>(null);
  const [restricted, setRestricted] = useState(false);
  const [enabled, setEnabled] = useState<Set<string>>(new Set());
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [busy, setBusy] = useState(false);

  const activeIntents = intents.filter(i => i.active);
  const current = projects.find(p => p.code === selected) || null;
  const intentName = (code: string) =>
    intents.find(i => i.code === code)?.displayName || code;

  const select = (p: Project) => {
    if (selected === p.code) { setSelected(null); return; }
    setSelected(p.code);
    setRestricted(p.restricted);
    setEnabled(new Set(p.enabledIntents));
  };

  const saveWhitelist = async () => {
    if (!current) return;
    if (restricted && enabled.size === 0 && !window.confirm(
      `Save with NO request types selected?\n\n${current.code} will handle nothing — every ` +
      `recognized request gets the "not available" message.`)) return;
    setBusy(true);
    try {
      await registry.setProjectIntents(current.code, restricted, Array.from(enabled));
      toast(restricted
        ? `${current.code} now handles: ${Array.from(enabled).map(intentName).join(', ') || 'nothing'}`
        : `${current.code} now handles every request type`);
      await reload();
    } catch (e: any) {
      toast('Could not save: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (p: Project) => {
    if (!window.confirm(p.active
      ? `Deactivate ${p.code}?\n\nIt disappears from selectors (existing conversations are unaffected).`
      : `Reactivate ${p.code}?`)) return;
    try {
      await registry.updateProject(p.code, { active: !p.active });
      toast(`${p.code} is now ${p.active ? 'inactive' : 'active'}`);
      await reload();
    } catch (e: any) {
      toast('Could not update: ' + e.message, true);
    }
  };

  const create = async () => {
    if (!newCode.trim()) return;
    setBusy(true);
    try {
      const created = await registry.createProject(newCode, newName);
      toast(`Project ${created.code} created — it handles every request type until you restrict it, `
        + `and uses the shared company details until you add its own below`);
      setNewCode('');
      setNewName('');
      await reload();
    } catch (e: any) {
      toast('Could not create: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2>2 · Projects</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        A project is one client or brand this assistant works for — one bank, one insurer, one
        hotel chain. “Handles: all” means every request type above works there. Restricting a
        project means only the ticked request types work; anything else gets that type’s
        “not available” message. Request types added later stay OFF for restricted projects until
        you tick them.
      </p>
      <table>
        <thead>
          <tr><th>Project</th><th>Display name</th><th>Handles</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {projects.map(p => (
            <tr key={p.code} className={'selectable' + (selected === p.code ? ' selected' : '')}
                onClick={() => select(p)}>
              <td className="name">{p.code}</td>
              <td>{p.displayName}</td>
              <td>{p.restricted
                ? (p.enabledIntents.length
                    ? p.enabledIntents.map(intentName).join(', ')
                    : <span className="pill down">nothing — customers always get “not available”</span>)
                : 'all request types'}</td>
              <td><span className={'pill' + (p.active ? '' : ' down')}>{p.active ? 'active' : 'inactive'}</span></td>
              <td onClick={e => e.stopPropagation()}>
                <button className="ghost btn" onClick={() => toggleActive(p)}>
                  {p.active ? 'Deactivate' : 'Activate'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {current && (
        <div style={{ marginTop: 14 }}>
          <h3 style={{ margin: '0 0 8px' }}>Which request types does {current.code} handle?</h3>
          <label style={{ display: 'block', marginBottom: 8, fontSize: 14, color: 'var(--text)' }}>
            <input type="checkbox" checked={restricted}
                   onChange={e => setRestricted(e.target.checked)} />{' '}
            Limit this project to selected request types only
          </label>
          {restricted && (
            <div className="radio-row" style={{ flexWrap: 'wrap' }}>
              {activeIntents.map(i => (
                <label key={i.code}>
                  <input type="checkbox" checked={enabled.has(i.code)}
                         onChange={e => {
                           const next = new Set(enabled);
                           if (e.target.checked) next.add(i.code); else next.delete(i.code);
                           setEnabled(next);
                         }} />
                  {i.displayName || i.code}
                </label>
              ))}
            </div>
          )}
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={saveWhitelist} disabled={busy}>
              {busy ? 'Saving…' : 'Save — live immediately'}
            </button>
            {!restricted && <span className="hint">Unlimited: every request type above works here.</span>}
          </div>
        </div>
      )}

      <div className="row" style={{ marginTop: 14 }}>
        <div>
          <label>Code — CAPITALS, permanent once created</label>
          <input type="text" placeholder="e.g. HOSPITALITY" value={newCode}
                 onChange={e => setNewCode(e.target.value)} style={{ width: 220 }} />
        </div>
        <div>
          <label>Display name</label>
          <input type="text" placeholder="e.g. Grand Hotel Group" value={newName}
                 onChange={e => setNewName(e.target.value)} style={{ width: 240 }} />
        </div>
        <button className="btn" onClick={create} disabled={busy || !newCode.trim()}>Add project</button>
      </div>
    </section>
  );
}

// ==================== 3. company / brand details ====================

function BrandsSection({ brands, projects, reload }:
    { brands: BrandAttribute[]; projects: Project[]; reload: () => Promise<void> }) {
  const [form, setForm] = useState({ projectCode: '', attrKey: '', attrValue: '' });
  const [editing, setEditing] = useState<BrandAttribute | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setBusy(true);
    try {
      await registry.upsertBrandAttribute(form.projectCode, form.attrKey, form.attrValue);
      toast(`"${form.attrKey}" saved for ${form.projectCode || 'all projects'} — used in live conversations from now on`);
      setForm({ projectCode: form.projectCode, attrKey: '', attrValue: '' });
      setEditing(null);
      await reload();
    } catch (e: any) {
      toast('Could not save: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const edit = (b: BrandAttribute) => {
    setEditing(b);
    setForm({ projectCode: b.projectCode || '', attrKey: b.attrKey, attrValue: b.attrValue });
  };

  const remove = async (b: BrandAttribute) => {
    const scope = b.projectCode || 'ALL projects (shared value)';
    if (!window.confirm(
      `Delete "${b.attrKey}" for ${scope}?\n\n` +
      (b.projectCode
        ? 'That project falls back to the shared value.'
        : 'This is the SHARED value — any AI instruction still using this slot will start failing loudly.'))) return;
    try {
      await registry.deleteBrandAttribute(b.id);
      toast(`"${b.attrKey}" deleted for ${scope}`);
      await reload();
    } catch (e: any) {
      toast('Could not delete: ' + e.message, true);
    }
  };

  return (
    <section className="panel">
      <h2>3 · Company details</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        Values that fill the curly-brace slots in the AI instructions — <code>bank_name</code>,{' '}
        <code>hotline</code>, <code>loan_email</code>, or any slot a template uses. A project’s own
        value wins; “Shared” is the fallback used by projects without one. Edits are live
        immediately.
      </p>
      <table>
        <thead>
          <tr><th>Used by</th><th>Slot name</th><th>Value</th><th /></tr>
        </thead>
        <tbody>
          {brands.map(b => (
            <tr key={b.id}>
              <td>{b.projectCode || <span className="tag">Shared (all projects)</span>}</td>
              <td><code>{'{' + b.attrKey + '}'}</code></td>
              <td>{b.attrValue}</td>
              <td>
                <button className="ghost btn" onClick={() => edit(b)}>Edit</button>{' '}
                <button className="ghost btn" onClick={() => remove(b)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{ marginTop: 14 }}>
        {editing && (
          <p className="hint" style={{ margin: '0 0 6px' }}>
            Editing <b>{editing.attrKey}</b> for <b>{editing.projectCode || 'all projects'}</b> —{' '}
            <button className="link" onClick={() => { setEditing(null); setForm({ projectCode: '', attrKey: '', attrValue: '' }); }}>
              cancel
            </button>
          </p>
        )}
        <div className="row">
          <div>
            <label>Used by</label>
            <select value={form.projectCode} disabled={!!editing}
                    onChange={e => setForm({ ...form, projectCode: e.target.value })}>
              <option value="">Shared (all projects)</option>
              {projects.map(p => <option key={p.code} value={p.code}>{p.code}</option>)}
            </select>
          </div>
          <div>
            <label>Slot name (as used in the instructions)</label>
            <input type="text" placeholder="e.g. bank_name" value={form.attrKey} disabled={!!editing}
                   onChange={e => setForm({ ...form, attrKey: e.target.value })} style={{ width: 200 }} />
          </div>
          <div>
            <label>Value</label>
            <input type="text" placeholder="e.g. Metrobank" value={form.attrValue}
                   onChange={e => setForm({ ...form, attrValue: e.target.value })} style={{ width: 280 }} />
          </div>
          <button className="btn" onClick={save}
                  disabled={busy || !form.attrKey.trim() || !form.attrValue.trim()}>
            {busy ? 'Saving…' : editing ? 'Save change' : 'Add value'}
          </button>
        </div>
      </div>
    </section>
  );
}
