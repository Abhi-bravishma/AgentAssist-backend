import { useEffect, useState } from 'react';
import { admin, RegistryStatus } from '../api';
import { toast } from '../toast';

export default function SettingsPage({ onProviderChanged }: { onProviderChanged: () => void }) {
  const [active, setActive] = useState('');
  const [available, setAvailable] = useState<string[]>([]);
  const [choice, setChoice] = useState('');
  const [saving, setSaving] = useState(false);
  const [registry, setRegistry] = useState<RegistryStatus | null>(null);
  const [evicting, setEvicting] = useState(false);

  const load = async () => {
    try {
      const p = await admin.aiProvider();
      setActive(p.active);
      setAvailable(p.available);
      setChoice(p.active);
    } catch (e: any) {
      toast('Could not load provider: ' + e.message, true);
    }
    try {
      setRegistry(await admin.registryStatus());
    } catch {
      setRegistry(null);
    }
  };

  useEffect(() => { load(); }, []);

  const apply = async () => {
    if (!choice || choice === active) return;
    if (!window.confirm(
      `Switch the AI provider to ${choice.toUpperCase()}?\n\nEvery analysis, translation and suggestion call switches immediately — no restart.`)) return;
    setSaving(true);
    try {
      const r = await admin.setAiProvider(choice);
      setActive(r.active);
      toast(`AI provider is now ${r.active}`);
      onProviderChanged();
    } catch (e: any) {
      toast('Switch failed: ' + e.message, true);
      setChoice(active);
    } finally {
      setSaving(false);
    }
  };

  const evict = async () => {
    setEvicting(true);
    try {
      await admin.evictCaches();
      toast('Registry caches evicted — edits are live now');
    } catch (e: any) {
      toast('Evict failed: ' + e.message, true);
    } finally {
      setEvicting(false);
    }
  };

  return (
    <>
      <section className="panel">
        <h2>AI provider</h2>
        <p className="hint" style={{ marginTop: 0 }}>
          One provider is active at a time. Ollama appears here but has no server yet —
          selecting it fails until the server URL is configured.
        </p>
        <div className="row">
          <div className="radio-row">
            {['openai', 'ollama'].map(p => (
              <label key={p}>
                <input type="radio" name="provider" value={p}
                       checked={choice === p}
                       disabled={!available.includes(p)}
                       onChange={() => setChoice(p)} />
                {p.toUpperCase()}
                {p === active && <span className="tag">active</span>}
                {!available.includes(p) && <span className="hint">(not available)</span>}
              </label>
            ))}
          </div>
          <button className="btn" onClick={apply} disabled={saving || choice === active}>
            {saving ? 'Switching…' : 'Apply'}
          </button>
        </div>
      </section>

      <section className="panel">
        <h2>Config registry</h2>
        {registry === null ? (
          <div className="hint">Status unavailable.</div>
        ) : registry.healthy ? (
          <p style={{ margin: 0 }}>
            <span className="pill">healthy</span>{' '}
            <span className="hint">All template keys present and published.</span>
          </p>
        ) : (
          <>
            <p style={{ marginTop: 0 }}><span className="pill down">incomplete</span></p>
            {registry.error && <p className="hint">Error: {registry.error}</p>}
            {registry.missingTemplateKeys.length > 0 && (
              <p className="hint">Missing keys: {registry.missingTemplateKeys.join(', ')}</p>
            )}
          </>
        )}
        <div className="row" style={{ marginTop: 14 }}>
          <button className="ghost btn" onClick={evict} disabled={evicting}>
            {evicting ? 'Evicting…' : 'Evict caches'}
          </button>
          <span className="hint">
            Registry edits are live within 60s anyway; evicting makes them instant.
          </span>
        </div>
      </section>
    </>
  );
}
