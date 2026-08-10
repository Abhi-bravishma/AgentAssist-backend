import { useEffect, useState } from 'react';
import { BrandAttribute, Intent, Project, registry } from '../api';
import { toast } from '../toast';

/**
 * Part 5: CRUD over the config registry — projects, intents, the
 * project→intent whitelist and brand attributes. Everything here is pure
 * data: a new intent flows into the classifier on its next call, whitelist
 * and brand edits are live immediately (caches evicted server-side).
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
      toast('Could not load registry: ' + e.message, true);
    }
  };

  useEffect(() => { reload(); }, []);

  return (
    <>
      <ProjectsSection projects={projects} intents={intents} reload={reload} />
      <IntentsSection intents={intents} reload={reload} />
      <BrandsSection brands={brands} projects={projects} reload={reload} />
    </>
  );
}

// ==================== projects + whitelist ====================

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

  const select = (p: Project) => {
    if (selected === p.code) { setSelected(null); return; }
    setSelected(p.code);
    setRestricted(p.restricted);
    setEnabled(new Set(p.enabledIntents));
  };

  const saveWhitelist = async () => {
    if (!current) return;
    setBusy(true);
    try {
      await registry.setProjectIntents(current.code, restricted, Array.from(enabled));
      toast(`${current.code}: ${restricted ? 'whitelist saved' : 'all intents allowed'}`);
      await reload();
    } catch (e: any) {
      toast('Save failed: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (p: Project) => {
    try {
      await registry.updateProject(p.code, { active: !p.active });
      toast(`${p.code} is now ${p.active ? 'inactive' : 'active'}`);
      await reload();
    } catch (e: any) {
      toast('Update failed: ' + e.message, true);
    }
  };

  const create = async () => {
    if (!newCode.trim()) return;
    setBusy(true);
    try {
      const created = await registry.createProject(newCode, newName);
      toast(`Project ${created.code} created — all intents allowed, brand values fall back to global defaults`);
      setNewCode('');
      setNewName('');
      await reload();
    } catch (e: any) {
      toast('Create failed: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2>Projects</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        A project with no whitelist allows every intent. Restricting stores the matrix explicitly —
        intents added later stay off for restricted projects until enabled here.
      </p>
      <table>
        <thead>
          <tr><th>Code</th><th>Display name</th><th>Intents allowed</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {projects.map(p => (
            <tr key={p.code} className={'selectable' + (selected === p.code ? ' selected' : '')}
                onClick={() => select(p)}>
              <td>{p.code}</td>
              <td>{p.displayName}</td>
              <td>{p.restricted ? (p.enabledIntents.join(', ') || '(none)') : 'all'}</td>
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
          <h3 style={{ margin: '0 0 8px' }}>Intents for {current.code}</h3>
          <label style={{ display: 'block', marginBottom: 8 }}>
            <input type="checkbox" checked={restricted}
                   onChange={e => setRestricted(e.target.checked)} />{' '}
            Restrict this project to selected intents
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
                  {i.code}
                </label>
              ))}
            </div>
          )}
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={saveWhitelist} disabled={busy}>
              {busy ? 'Saving…' : 'Save whitelist'}
            </button>
            {!restricted && <span className="hint">Unrestricted: every intent is allowed.</span>}
          </div>
        </div>
      )}

      <div className="row" style={{ marginTop: 14 }}>
        <input type="text" placeholder="CODE (e.g. HOSPITALITY)" value={newCode}
               onChange={e => setNewCode(e.target.value)} style={{ width: 220 }} />
        <input type="text" placeholder="Display name" value={newName}
               onChange={e => setNewName(e.target.value)} style={{ width: 240 }} />
        <button className="btn" onClick={create} disabled={busy || !newCode.trim()}>Add project</button>
      </div>
    </section>
  );
}

// ==================== intents ====================

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
      toast(`${current.code} saved — the classifier uses it on its next call`);
      await reload();
    } catch (e: any) {
      toast('Save failed: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (i: Intent) => {
    const msg = i.active
      ? `Deactivate ${i.code}?\n\nThe classifier stops returning it immediately.`
      : `Reactivate ${i.code}?`;
    if (!window.confirm(msg)) return;
    try {
      await registry.updateIntent(i.code, { active: !i.active });
      toast(`${i.code} is now ${i.active ? 'inactive' : 'active'}`);
      await reload();
    } catch (e: any) {
      toast('Update failed: ' + e.message, true);
    }
  };

  const doCreate = async () => {
    setBusy(true);
    try {
      const made = await registry.createIntent(create);
      toast(`Intent ${made.code} created — live for classification now. `
        + `Optional: publish a checklist.${made.code.toLowerCase()} template in Prompts.`);
      setCreate({ code: '', displayName: '', description: '', filteredMessageTemplate: '' });
      setCreating(false);
      await reload();
    } catch (e: any) {
      toast('Create failed: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2>Intents</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        The description IS the classifier rule (tell the model when to return this code).
        The filtered message is what the agent sees when a project does not offer the intent
        — <code>{'{project}'}</code> is replaced with the project name.
      </p>
      <table>
        <thead>
          <tr><th>Code</th><th>Display name</th><th>Rule</th><th>Filtered msg</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {intents.map(i => (
            <tr key={i.code} className={'selectable' + (selected === i.code ? ' selected' : '')}
                onClick={() => select(i)}>
              <td>{i.code}</td>
              <td>{i.displayName}</td>
              <td>{i.description ? 'yes' : <span className="pill down">missing</span>}</td>
              <td>{i.filteredMessageTemplate ? 'yes' : '—'}</td>
              <td><span className={'pill' + (i.active ? '' : ' down')}>{i.active ? 'active' : 'inactive'}</span></td>
              <td onClick={e => e.stopPropagation()}>
                <button className="ghost btn" onClick={() => toggleActive(i)}>
                  {i.active ? 'Deactivate' : 'Activate'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {current && (
        <div style={{ marginTop: 14 }}>
          <h3 style={{ margin: '0 0 8px' }}>Edit {current.code}</h3>
          <div className="row">
            <input type="text" placeholder="Display name" value={form.displayName}
                   onChange={e => setForm({ ...form, displayName: e.target.value })}
                   style={{ width: 260 }} />
          </div>
          <textarea rows={4} placeholder='Classifier rule, e.g. Return "ROOM_BOOKING" if the customer asks to book a room.'
                    value={form.description}
                    onChange={e => setForm({ ...form, description: e.target.value })}
                    style={{ width: '100%', marginTop: 8 }} />
          <textarea rows={2} placeholder="Filtered message, e.g. Room booking is not available through {project}."
                    value={form.filteredMessageTemplate}
                    onChange={e => setForm({ ...form, filteredMessageTemplate: e.target.value })}
                    style={{ width: '100%', marginTop: 8 }} />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={save} disabled={busy || !form.description.trim()}>
              {busy ? 'Saving…' : 'Save intent'}
            </button>
          </div>
        </div>
      )}

      {creating ? (
        <div style={{ marginTop: 14 }}>
          <h3 style={{ margin: '0 0 8px' }}>New intent</h3>
          <div className="row">
            <input type="text" placeholder="CODE (e.g. ROOM_BOOKING)" value={create.code}
                   onChange={e => setCreate({ ...create, code: e.target.value })} style={{ width: 220 }} />
            <input type="text" placeholder="Display name" value={create.displayName}
                   onChange={e => setCreate({ ...create, displayName: e.target.value })} style={{ width: 240 }} />
          </div>
          <textarea rows={4} placeholder='Classifier rule (required), e.g. Return "ROOM_BOOKING" if the customer asks to book, change or cancel a room reservation.'
                    value={create.description}
                    onChange={e => setCreate({ ...create, description: e.target.value })}
                    style={{ width: '100%', marginTop: 8 }} />
          <textarea rows={2} placeholder="Filtered message (optional), supports {project}"
                    value={create.filteredMessageTemplate}
                    onChange={e => setCreate({ ...create, filteredMessageTemplate: e.target.value })}
                    style={{ width: '100%', marginTop: 8 }} />
          <div className="row" style={{ marginTop: 10 }}>
            <button className="btn" onClick={doCreate}
                    disabled={busy || !create.code.trim() || !create.description.trim()}>
              {busy ? 'Creating…' : 'Create intent'}
            </button>
            <button className="ghost btn" onClick={() => setCreating(false)}>Cancel</button>
          </div>
        </div>
      ) : (
        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn" onClick={() => setCreating(true)}>Add intent</button>
        </div>
      )}
    </section>
  );
}

// ==================== brand attributes ====================

function BrandsSection({ brands, projects, reload }:
    { brands: BrandAttribute[]; projects: Project[]; reload: () => Promise<void> }) {
  const [form, setForm] = useState({ projectCode: '', attrKey: '', attrValue: '' });
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setBusy(true);
    try {
      await registry.upsertBrandAttribute(form.projectCode, form.attrKey, form.attrValue);
      toast(`${form.attrKey} saved for ${form.projectCode || 'GLOBAL DEFAULT'} — live in prompts now`);
      setForm({ projectCode: form.projectCode, attrKey: '', attrValue: '' });
      await reload();
    } catch (e: any) {
      toast('Save failed: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  const edit = (b: BrandAttribute) =>
    setForm({ projectCode: b.projectCode || '', attrKey: b.attrKey, attrValue: b.attrValue });

  const remove = async (b: BrandAttribute) => {
    const scope = b.projectCode || 'GLOBAL DEFAULT';
    if (!window.confirm(
      `Delete ${b.attrKey} for ${scope}?\n\n` +
      (b.projectCode
        ? 'Lookups fall back to the global default.'
        : 'This is a GLOBAL DEFAULT — any checklist still referencing it will fail loudly.'))) return;
    try {
      await registry.deleteBrandAttribute(b.id);
      toast(`${b.attrKey} deleted for ${scope}`);
      await reload();
    } catch (e: any) {
      toast('Delete failed: ' + e.message, true);
    }
  };

  return (
    <section className="panel">
      <h2>Brand attributes</h2>
      <p className="hint" style={{ marginTop: 0 }}>
        Values substituted into checklist prompts: <code>bank_name</code>, <code>hotline</code>,{' '}
        <code>loan_email</code> (and any key a template references). Project value wins; global
        default is the fallback. Edits are live immediately.
      </p>
      <table>
        <thead>
          <tr><th>Scope</th><th>Key</th><th>Value</th><th /></tr>
        </thead>
        <tbody>
          {brands.map(b => (
            <tr key={b.id}>
              <td>{b.projectCode || <span className="tag">global default</span>}</td>
              <td>{b.attrKey}</td>
              <td>{b.attrValue}</td>
              <td>
                <button className="ghost btn" onClick={() => edit(b)}>Edit</button>{' '}
                <button className="ghost btn" onClick={() => remove(b)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="row" style={{ marginTop: 14 }}>
        <select value={form.projectCode}
                onChange={e => setForm({ ...form, projectCode: e.target.value })}>
          <option value="">Global default</option>
          {projects.map(p => <option key={p.code} value={p.code}>{p.code}</option>)}
        </select>
        <input type="text" placeholder="key (e.g. bank_name)" value={form.attrKey}
               onChange={e => setForm({ ...form, attrKey: e.target.value })} style={{ width: 200 }} />
        <input type="text" placeholder="value" value={form.attrValue}
               onChange={e => setForm({ ...form, attrValue: e.target.value })} style={{ width: 280 }} />
        <button className="btn" onClick={save}
                disabled={busy || !form.attrKey.trim() || !form.attrValue.trim()}>
          {busy ? 'Saving…' : 'Save'}
        </button>
      </div>
    </section>
  );
}
