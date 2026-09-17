import { useEffect, useMemo, useRef, useState } from 'react';
import { kb, KbDocument, registry } from '../api';
import { toast } from '../toast';

const PAGE_SIZE = 20;
/** Server rejects anything larger; catching it here saves the upload wait. */
const MAX_FILE_MB = 100;

type LoadState = 'idle' | 'loading' | 'error';

/** "2 hours ago", with the exact stamp on hover. */
function relative(iso?: string | null): { label: string; title: string } {
  if (!iso) return { label: '—', title: '' };
  const then = new Date(iso);
  if (isNaN(then.getTime())) return { label: iso, title: iso };
  const mins = Math.round((Date.now() - then.getTime()) / 60000);
  if (mins < 1) return { label: 'just now', title: then.toLocaleString() };
  if (mins < 60) return { label: `${mins} min ago`, title: then.toLocaleString() };
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return { label: `${hrs} hour${hrs === 1 ? '' : 's'} ago`, title: then.toLocaleString() };
  const days = Math.round(hrs / 24);
  if (days < 30) return { label: `${days} day${days === 1 ? '' : 's'} ago`, title: then.toLocaleString() };
  return { label: then.toLocaleDateString(), title: then.toLocaleString() };
}

function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentsPage() {
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filter, setFilter] = useState('');
  const [state, setState] = useState<LoadState>('idle');
  const [errorMsg, setErrorMsg] = useState('');
  const [uploading, setUploading] = useState(false);
  const [projects, setProjects] = useState<string[]>([]);
  const [queued, setQueued] = useState<File[]>([]);
  const [dragging, setDragging] = useState(false);
  const [confirmDoc, setConfirmDoc] = useState<KbDocument | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [project, setProject] = useState('');
  const [category, setCategory] = useState('');

  const load = async (p = page, proj = filter) => {
    setState('loading');
    try {
      const r = await kb.list(p, PAGE_SIZE, proj || undefined);
      setDocs(r.documents ?? []);
      setTotal(r.totalDocuments);
      setTotalPages(Math.max(r.totalPages, 1));
      setState('idle');
      setErrorMsg('');
    } catch (e: any) {
      // Distinct from empty: the list is unknown, not known to be zero.
      setState('error');
      setErrorMsg(e.message || 'Unknown error');
      setDocs([]);
    }
  };

  useEffect(() => {
    load(page, filter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, filter]);

  // The dropdown lists every registered project, not only those on this page.
  useEffect(() => {
    registry.projects()
      .then(ps => setProjects(ps.map(p => p.code).sort()))
      .catch(() => setProjects([]));
  }, []);

  const totalQueuedMb = useMemo(
    () => queued.reduce((s, f) => s + f.size, 0) / (1024 * 1024),
    [queued],
  );

  const addFiles = (list: FileList | null) => {
    if (!list) return;
    const incoming = Array.from(list);
    const tooBig = incoming.filter(f => f.size > MAX_FILE_MB * 1024 * 1024);
    if (tooBig.length) {
      toast(`${tooBig.map(f => f.name).join(', ')} — over ${MAX_FILE_MB}MB`, true);
    }
    const ok = incoming.filter(f => f.size <= MAX_FILE_MB * 1024 * 1024);
    setQueued(q => [...q, ...ok.filter(f => !q.some(e => e.name === f.name && e.size === f.size))]);
  };

  const upload = async () => {
    if (!queued.length) return toast('Choose at least one file', true);
    setUploading(true);
    try {
      const dt = new DataTransfer();
      queued.forEach(f => dt.items.add(f));
      const r = await kb.upload(dt.files, project.trim(), category.trim());
      toast(`${r.processedFiles} uploaded` + (r.failedFiles ? `, ${r.failedFiles} failed` : ''),
        r.failedFiles > 0);
      setQueued([]);
      if (fileRef.current) fileRef.current.value = '';
      setPage(0);
      await load(0, filter);
    } catch (e: any) {
      toast('Upload failed: ' + e.message, true);
    } finally {
      setUploading(false);
    }
  };

  const togglePause = async (d: KbDocument) => {
    const makeActive = d.active === false;
    try {
      await kb.setActive(d.fileName, makeActive);
      toast(makeActive ? 'Document resumed' : 'Document paused');
      await load(page, filter);
    } catch (e: any) {
      toast('Could not update: ' + e.message, true);
    }
  };

  const remove = async (d: KbDocument) => {
    setConfirmDoc(null);
    try {
      await kb.remove(d.fileName);
      toast('Document deleted');
      // Deleting the last row of the last page used to strand you on an empty
      // page, which looked like everything had gone.
      const remaining = total - 1;
      const lastPage = Math.max(0, Math.ceil(remaining / PAGE_SIZE) - 1);
      const target = Math.min(page, lastPage);
      if (target !== page) setPage(target);
      else await load(target, filter);
    } catch (e: any) {
      toast('Delete failed: ' + e.message, true);
    }
  };

  const busy = state === 'loading';

  return (
    <>
      <section className="panel">
        <h2>Upload documents</h2>

        <div
          className={'dropzone' + (dragging ? ' over' : '')}
          onDragOver={e => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => { e.preventDefault(); setDragging(false); addFiles(e.dataTransfer.files); }}
          onClick={() => fileRef.current?.click()}
        >
          <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 16V4m0 0L8 8m4-4 4 4" />
            <path d="M3 15v3a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-3" />
          </svg>
          <div>
            <strong>Drop files here</strong> or click to browse
            <div className="hint">PDF, DOCX, DOC, TXT, MD, XLSX, CSV, PPTX — up to {MAX_FILE_MB}MB each</div>
          </div>
        </div>
        <input type="file" ref={fileRef} multiple hidden
               onChange={e => addFiles(e.target.files)} />

        {queued.length > 0 && (
          <div className="chips">
            {queued.map(f => (
              <span className="chip" key={f.name + f.size}>
                {f.name}<span className="chip-size">{humanSize(f.size)}</span>
                <button className="chip-x" aria-label={`Remove ${f.name}`}
                        onClick={() => setQueued(q => q.filter(x => x !== f))}>×</button>
              </span>
            ))}
            <span className="hint">
              {queued.length} file{queued.length === 1 ? '' : 's'}, {totalQueuedMb.toFixed(1)}MB
            </span>
          </div>
        )}

        <div className="row">
          <div>
            <label htmlFor="doc-project">Project</label>
            <input id="doc-project" type="text" list="projects" value={project}
                   onChange={e => setProject(e.target.value)} placeholder="e.g. HOSPITALITY" />
            <datalist id="projects">
              {projects.map(p => <option key={p} value={p} />)}
            </datalist>
          </div>
          <div>
            <label htmlFor="doc-category">Category (optional)</label>
            <input id="doc-category" type="text" value={category}
                   onChange={e => setCategory(e.target.value)} placeholder="e.g. dining" />
          </div>
          <span className="spacer" />
          <button className="btn" onClick={upload} disabled={uploading || !queued.length}>
            {uploading ? 'Uploading…' : `Upload${queued.length ? ` ${queued.length}` : ''}`}
          </button>
        </div>
        {uploading && (
          <div className="hint" style={{ marginTop: 8 }}>
            Embedding and indexing — roughly 4 seconds per chunk, so large files take a while.
          </div>
        )}
      </section>

      <section className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <h2 style={{ margin: 0 }}>Documents</h2>
          <span className="hint">
            {state === 'error' ? '—' : `${total} document${total === 1 ? '' : 's'}`}
          </span>
          <span className="spacer" />
          <div>
            <label htmlFor="doc-filter">Filter by project</label>
            <select id="doc-filter" value={filter}
                    onChange={e => { setPage(0); setFilter(e.target.value); }}>
              <option value="">All projects</option>
              {projects.map(p => <option key={p}>{p}</option>)}
            </select>
          </div>
          <button className="ghost btn" onClick={() => load(page, filter)} disabled={busy}>
            {busy ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {state === 'error' ? (
          <div className="empty error-state">
            <strong>Can&rsquo;t reach the knowledge base</strong>
            <div className="hint">{errorMsg}</div>
            <div className="hint">
              Your documents are still there &mdash; this page just cannot read them right now.
            </div>
            <button className="btn" style={{ marginTop: 12 }}
                    onClick={() => load(page, filter)}>Try again</button>
          </div>
        ) : (
          <>
            <div className="scroll">
              <table>
                <thead>
                  <tr>
                    <th>Document</th><th>Project</th><th>Category</th>
                    <th>Uploaded</th><th>Chunks</th><th>State</th><th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {busy && docs.length === 0
                    ? Array.from({ length: 4 }).map((_, i) => (
                        <tr key={'sk' + i} className="skeleton-row">
                          {Array.from({ length: 7 }).map((__, j) => (
                            <td key={j}><span className="skeleton" /></td>
                          ))}
                        </tr>
                      ))
                    : docs.map(d => {
                        const on = d.active !== false;
                        const when = relative(d.uploadDate);
                        return (
                          <tr key={d.fileName} className={on ? '' : 'paused'}>
                            <td className="name">{d.fileName}</td>
                            <td>{d.projectName ? <span className="tag">{d.projectName}</span> : '—'}</td>
                            <td>{d.category ?? '—'}</td>
                            <td title={when.title}>{when.label}</td>
                            <td>{d.chunkCount}</td>
                            <td>
                              <span className={'state ' + (on ? 'on' : 'off')}>{on ? 'Active' : 'Paused'}</span>
                            </td>
                            <td className="actions">
                              <button className="link"
                                      onClick={() => window.open(kb.downloadUrl(d.fileName), '_blank')}>View</button>
                              <button className="link"
                                      onClick={() => togglePause(d)}>{on ? 'Pause' : 'Resume'}</button>
                              <button className="link danger"
                                      onClick={() => setConfirmDoc(d)}>Delete</button>
                            </td>
                          </tr>
                        );
                      })}
                </tbody>
              </table>
            </div>

            {!busy && docs.length === 0 && (
              <div className="empty">
                {filter
                  ? <>No documents in <span className="tag">{filter}</span>.</>
                  : <>Nothing uploaded yet. Drop a file above to build the knowledge base.</>}
              </div>
            )}

            <div className="foot">
              <button className="ghost btn" disabled={page === 0 || busy}
                      onClick={() => setPage(p => p - 1)}>Previous</button>
              <span className="hint">Page {page + 1} of {totalPages}</span>
              <button className="ghost btn" disabled={page >= totalPages - 1 || busy}
                      onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          </>
        )}
      </section>

      {confirmDoc && (
        <div className="modal-backdrop" onClick={() => setConfirmDoc(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3>Delete &ldquo;{confirmDoc.fileName}&rdquo;?</h3>
            <p className="hint">
              This removes it from the knowledge base permanently. To take it out of
              search temporarily, use Pause instead.
            </p>
            <div className="modal-foot">
              <button className="ghost btn" onClick={() => setConfirmDoc(null)}>Cancel</button>
              <button className="btn danger-btn" onClick={() => remove(confirmDoc)}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
