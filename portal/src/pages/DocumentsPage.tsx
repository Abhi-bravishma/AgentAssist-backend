import { useEffect, useMemo, useRef, useState } from 'react';
import { kb, KbDocument } from '../api';
import { toast } from '../toast';

const PAGE_SIZE = 20;

export default function DocumentsPage() {
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filter, setFilter] = useState('');
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const [project, setProject] = useState('');
  const [category, setCategory] = useState('');

  const load = async (p = page) => {
    setBusy(true);
    try {
      const r = await kb.list(p, PAGE_SIZE);
      setDocs(r.documents ?? []);
      setTotal(r.totalDocuments);
      setTotalPages(Math.max(r.totalPages, 1));
    } catch (e: any) {
      toast('Could not load documents: ' + e.message, true);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    load(page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const projects = useMemo(
    () => [...new Set(docs.map(d => d.projectName).filter(Boolean))].sort() as string[],
    [docs],
  );
  const shown = filter ? docs.filter(d => d.projectName === filter) : docs;

  const upload = async () => {
    const files = fileRef.current?.files;
    if (!files || !files.length) return toast('Choose at least one file', true);
    setUploading(true);
    try {
      const r = await kb.upload(files, project.trim(), category.trim());
      toast(`${r.processedFiles} uploaded` + (r.failedFiles ? `, ${r.failedFiles} failed` : ''),
        r.failedFiles > 0);
      if (fileRef.current) fileRef.current.value = '';
      setPage(0);
      await load(0);
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
      await load();
    } catch (e: any) {
      toast('Could not update: ' + e.message, true);
    }
  };

  const remove = async (d: KbDocument) => {
    if (!window.confirm(`Delete "${d.fileName}"?\n\nThis removes it from the knowledge base permanently. To take it out of search temporarily, use Pause instead.`)) return;
    try {
      await kb.remove(d.fileName);
      toast('Document deleted');
      await load();
    } catch (e: any) {
      toast('Delete failed: ' + e.message, true);
    }
  };

  return (
    <>
      <section className="panel">
        <h2>Upload documents</h2>
        <div className="row">
          <div>
            <label>Files — PDF, DOCX, DOC, TXT, MD, XLSX, CSV, PPTX</label>
            <input type="file" ref={fileRef} multiple />
          </div>
          <div>
            <label>Project</label>
            <input type="text" list="projects" value={project}
                   onChange={e => setProject(e.target.value)} placeholder="e.g. HOSPITALITY" />
            <datalist id="projects">
              {projects.map(p => <option key={p} value={p} />)}
            </datalist>
          </div>
          <div>
            <label>Category (optional)</label>
            <input type="text" value={category}
                   onChange={e => setCategory(e.target.value)} placeholder="e.g. dining" />
          </div>
          <button className="btn" onClick={upload} disabled={uploading}>
            {uploading ? 'Uploading…' : 'Upload'}
          </button>
        </div>
      </section>

      <section className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <h2 style={{ margin: 0 }}>Documents</h2>
          <span className="hint">{total} document{total === 1 ? '' : 's'}</span>
          <span className="spacer" />
          <div>
            <label>Filter by project</label>
            <select value={filter} onChange={e => setFilter(e.target.value)}>
              <option value="">All projects</option>
              {projects.map(p => <option key={p}>{p}</option>)}
            </select>
          </div>
          <button className="ghost btn" onClick={() => load()} disabled={busy}>Refresh</button>
        </div>

        <div className="scroll">
          <table>
            <thead>
              <tr>
                <th>Document</th><th>Project</th><th>Category</th>
                <th>Uploaded</th><th>Chunks</th><th>State</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {shown.map(d => {
                const on = d.active !== false;
                return (
                  <tr key={d.fileName} className={on ? '' : 'paused'}>
                    <td className="name">{d.fileName}</td>
                    <td>{d.projectName ? <span className="tag">{d.projectName}</span> : '—'}</td>
                    <td>{d.category ?? '—'}</td>
                    <td>{d.uploadDate ?? '—'}</td>
                    <td>{d.chunkCount}</td>
                    <td><span className={'state ' + (on ? 'on' : 'off')}>{on ? 'Active' : 'Paused'}</span></td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <button className="link"
                              onClick={() => window.open(kb.downloadUrl(d.fileName), '_blank')}>View</button>
                      <button className="link" onClick={() => togglePause(d)}>{on ? 'Pause' : 'Resume'}</button>
                      <button className="link danger" onClick={() => remove(d)}>Delete</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {shown.length === 0 && <div className="empty">No documents.</div>}

        <div className="foot">
          <button className="ghost btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
          <span className="hint">Page {page + 1} of {totalPages}</span>
          <button className="ghost btn" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
        </div>
      </section>
    </>
  );
}
