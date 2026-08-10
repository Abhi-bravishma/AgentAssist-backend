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

// ==================== registry CRUD (Part 5) ====================

export interface Project {
  code: string;
  displayName: string;
  active: boolean;
  restricted: boolean; // false = ALL intents allowed (no whitelist rows)
  enabledIntents: string[];
}

export interface Intent {
  code: string;
  displayName: string;
  active: boolean;
  description: string | null; // the classifier rule
  filteredMessageTemplate: string | null; // {project} placeholder
}

export interface BrandAttribute {
  id: number;
  projectCode: string | null; // null = global default
  attrKey: string;
  attrValue: string;
}

const REG = '/api/v1/admin/registry';

export const registry = {
  projects: () => api<Project[]>(`${REG}/projects`),
  createProject: (code: string, displayName: string) =>
    api<Project>(`${REG}/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, displayName }),
    }),
  updateProject: (code: string, patch: { displayName?: string; active?: boolean }) =>
    api<Project>(`${REG}/projects/${encodeURIComponent(code)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    }),
  setProjectIntents: (code: string, restricted: boolean, enabled: string[]) =>
    api<Project>(`${REG}/projects/${encodeURIComponent(code)}/intents`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ restricted, enabled }),
    }),

  intents: () => api<Intent[]>(`${REG}/intents`),
  createIntent: (i: {
    code: string; displayName: string; description: string; filteredMessageTemplate: string;
  }) =>
    api<Intent>(`${REG}/intents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(i),
    }),
  updateIntent: (
    code: string,
    patch: { displayName?: string; description?: string; filteredMessageTemplate?: string; active?: boolean },
  ) =>
    api<Intent>(`${REG}/intents/${encodeURIComponent(code)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    }),

  brandAttributes: () => api<BrandAttribute[]>(`${REG}/brand-attributes`),
  upsertBrandAttribute: (projectCode: string, attrKey: string, attrValue: string) =>
    api<BrandAttribute>(`${REG}/brand-attributes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectCode, attrKey, attrValue }),
    }),
  deleteBrandAttribute: (id: number) =>
    api(`${REG}/brand-attributes/${id}`, { method: 'DELETE' }),
};
