/** Thin fetch wrapper: JSON in/out, backend error message extraction. */
export async function api<T = unknown>(path: string, opts: RequestInit = {}): Promise<T> {
  const res = await fetch(path, opts);
  const text = await res.text();
  let body: any = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  if (!res.ok) {
    throw new Error(body.error || body.message || body.raw || `HTTP ${res.status}`);
  }
  return body as T;
}

// ==================== knowledge base ====================

export interface KbDocument {
  fileName: string;
  category: string | null;
  uploadDate: string | null;
  chunkCount: number;
  projectName: string | null;
  active: boolean | null; // null/true = active (see backend note)
}

export interface KbDocumentList {
  documents: KbDocument[];
  totalDocuments: number;
  page: number;
  size: number;
  totalPages: number;
}

const KB = '/api/v1/knowledge-base';

export const kb = {
  status: () => api<{ enabled: boolean; message: string }>(`${KB}/status`),
  list: (page: number, size: number) =>
    api<KbDocumentList>(`${KB}/documents?page=${page}&size=${size}`),
  upload: (files: FileList, projectName: string, category: string) => {
    const fd = new FormData();
    for (const f of Array.from(files)) fd.append('files', f);
    const qs = new URLSearchParams();
    if (projectName) qs.set('projectName', projectName);
    if (category) qs.set('category', category);
    return api<{ processedFiles: number; failedFiles: number; failedFileNames?: string[] }>(
      `${KB}/documents/upload${qs.size ? '?' + qs : ''}`,
      { method: 'POST', body: fd },
    );
  },
  setActive: (fileName: string, active: boolean) =>
    api(`${KB}/documents/${encodeURIComponent(fileName)}/active`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ active }),
    }),
  remove: (fileName: string) =>
    api(`${KB}/documents/${encodeURIComponent(fileName)}`, { method: 'DELETE' }),
  downloadUrl: (fileName: string) => `${KB}/documents/download/${encodeURIComponent(fileName)}`,
};

// ==================== admin config ====================

export interface TemplateSummary {
  templateKey: string;
  projectCode: string | null;
  publishedVersion: number | null;
  latestVersion: number | null;
  latestStatus: string;
}

export interface TemplateContent {
  templateKey: string;
  projectCode: string | null;
  version: number;
  content: string;
}

export interface RegistryStatus {
  checked: boolean;
  healthy: boolean;
  missingTemplateKeys: string[];
  error?: string;
}

const CFG = '/api/v1/admin/config';

export const admin = {
  aiProvider: () => api<{ active: string; available: string[] }>(`${CFG}/ai-provider`),
  setAiProvider: (provider: string) =>
    api<{ active: string; available: string[] }>(`${CFG}/ai-provider`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider }),
    }),
  registryStatus: () => api<RegistryStatus>(`${CFG}/registry-status`),
  templates: () => api<TemplateSummary[]>(`${CFG}/templates`),
  templateContent: (key: string, project: string | null) => {
    const qs = new URLSearchParams({ key });
    if (project) qs.set('project', project);
    return api<TemplateContent>(`${CFG}/templates/content?${qs}`);
  },
  publishTemplate: (templateKey: string, projectCode: string | null, content: string) =>
    api<TemplateContent>(`${CFG}/templates`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ templateKey, projectCode: projectCode ?? '', content }),
    }),
  evictCaches: () => api<{ message: string }>(`${CFG}/cache/evict`, { method: 'POST' }),
};
