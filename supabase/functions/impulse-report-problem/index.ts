const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const MAX_DESCRIPTION_CHARS = 3000;
const MAX_REPORT_BODY_CHARS = 2000000;
const MAX_LOG_CHARS = 100000;
const MAX_REPORT_STORAGE_CHARS = 1800000;
const MAX_REPORTS_PER_INSTALLATION_PER_DAY = 8;
const MAX_CHUNK_TEXT_CHARS = 700;
const MAX_CHUNK_COUNT = 4000;
const MAX_CHUNKED_PAYLOAD_CHARS = 2200000;
const MAX_DECOMPRESSED_PAYLOAD_CHARS = 2200000;
const CHUNK_RETENTION_HOURS = 6;
const LOG_STORAGE_BUCKET = "impulse-problem-report-logs";

const jsonHeaders = {
  "Content-Type": "application/json; charset=utf-8",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return jsonResponse({ ok: true });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  if (!isAllowedClientKey(req.headers.get("apikey"))) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  let payload: unknown;
  try {
    payload = await req.json();
  } catch (_error) {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const serviceKey = getServiceKey();
  if (!SUPABASE_URL || !serviceKey) {
    return jsonResponse({ error: "backend_not_configured" }, 500);
  }

  if (isChunkPayload(payload)) {
    return handleChunkedReport(req, serviceKey, payload);
  }

  const reportPayload = payload as ReportPayload;
  const validationError = validatePayload(reportPayload);
  if (validationError) {
    return jsonResponse({ error: validationError }, 400);
  }

  return handleCompleteReport(req, serviceKey, reportPayload, { chunked: false });
});

async function handleCompleteReport(
  req: Request,
  serviceKey: string,
  payload: ReportPayload,
  options: { chunked: boolean; chunkSessionId?: string },
): Promise<Response> {
  const installationId = payload.installationId.trim();
  const dailyCount = await countReportsForInstallation(serviceKey, installationId);
  if (dailyCount >= MAX_REPORTS_PER_INSTALLATION_PER_DAY) {
    return jsonResponse({ error: "rate_limited" }, 429);
  }

  const reportBody = truncate(payload.reportBody, MAX_REPORT_BODY_CHARS);
  const logExcerpt = truncate(payload.logExcerpt ?? "", MAX_LOG_CHARS);
  const ipHash = await hashIp(req.headers.get("x-forwarded-for"));

  const row = {
    installation_id: installationId,
    title: truncate(payload.title || "[Relato] Problema no Impulse", 120),
    description: truncate(payload.description.trim(), MAX_DESCRIPTION_CHARS),
    app_version_name: truncate(payload.app?.versionName ?? "unknown", 80),
    app_version_code: numberOrZero(payload.app?.versionCode),
    app_build_type: truncate(payload.app?.buildType ?? "unknown", 40),
    app_package_name: truncate(payload.app?.packageName ?? "unknown", 120),
    app_debug: Boolean(payload.app?.debug),
    generated_at: truncate(payload.generatedAt ?? "", 80),
    client_timezone: truncate(payload.timeZone ?? "", 80),
    carplay_connected: Boolean(payload.context?.carPlayConnected),
    android_auto_connected: Boolean(payload.context?.androidAutoConnected),
    mirrored_on_d3: Boolean(payload.context?.mirroredOnD3),
    not_mirrored_on_d3: Boolean(payload.context?.notMirroredOnD3),
    map_returned_to_normal: Boolean(payload.context?.mapReturnedToNormal),
    ac_returned_to_mainmenu: Boolean(payload.context?.acReturnedToMainMenu),
    report_body: reportBody,
    log_excerpt: logExcerpt,
    log_file_name: truncate(payload.logFileName ?? "", 160),
    log_char_count: numberOrZero(payload.logCharCount),
    raw_payload: {
      context: payload.context ?? {},
      app: payload.app ?? {},
      generatedAt: payload.generatedAt ?? "",
      timeZone: payload.timeZone ?? "",
      logFileName: payload.logFileName ?? "",
      logCharCount: payload.logCharCount ?? 0,
      chunked: options.chunked,
      chunkSessionId: options.chunkSessionId ?? null,
    },
    request_ip_hash: ipHash,
    user_agent: truncate(req.headers.get("user-agent") ?? "", 240),
  };

  const inserted = await insertReport(serviceKey, row);
  if (!inserted.ok) {
    return jsonResponse({ error: "insert_failed", detail: inserted.error }, 500);
  }

  const logStorage = await uploadReportFile(serviceKey, inserted.id, payload, reportBody);
  if (logStorage.ok) {
    await updateReportLogStorage(serviceKey, inserted.id, logStorage);
  }

  const issue = await maybeCreateGithubIssue(payload.title, reportBody, inserted.id);
  if (issue.githubIssueUrl) {
    await updateGithubIssue(serviceKey, inserted.id, issue.githubIssueUrl, issue.githubIssueNumber);
  }

  return jsonResponse({
    id: inserted.id,
    githubIssueUrl: issue.githubIssueUrl,
    githubIssueNumber: issue.githubIssueNumber,
    logStoragePath: logStorage.ok ? logStorage.path : null,
    logStorageError: logStorage.ok ? null : logStorage.error,
  });
}

type ReportPayload = {
  installationId: string;
  title?: string;
  description: string;
  context?: {
    carPlayConnected?: boolean;
    androidAutoConnected?: boolean;
    mirroredOnD3?: boolean;
    notMirroredOnD3?: boolean;
    mapReturnedToNormal?: boolean;
    acReturnedToMainMenu?: boolean;
  };
  app?: {
    packageName?: string;
    versionName?: string;
    versionCode?: number;
    buildType?: string;
    debug?: boolean;
  };
  generatedAt?: string;
  timeZone?: string;
  reportBody: string;
  logExcerpt?: string;
  logFileName?: string;
  logCharCount?: number;
};

type ReportChunkPayload = {
  uploadMode: "chunk";
  payloadEncoding?: "plain-json" | "gzip-base64-json";
  sessionId: string;
  installationId: string;
  chunkIndex: number;
  chunkCount: number;
  chunkText: string;
  finalChunk?: boolean;
};

type InsertResult = { ok: true; id: string } | { ok: false; error: string };

type ChunkRow = { chunk_index: number; chunk_text: string };

type ReportFileUploadResult =
  | { ok: true; bucket: string; path: string; sizeBytes: number }
  | { ok: false; error: string };

function validatePayload(payload: ReportPayload): string | null {
  if (!payload || typeof payload !== "object") return "missing_payload";
  if (!payload.installationId || payload.installationId.trim().length < 8) {
    return "missing_installation_id";
  }
  const description = payload.description?.trim() ?? "";
  if (description.length < 10) return "description_too_short";
  if (description.length > MAX_DESCRIPTION_CHARS) return "description_too_long";
  if (!payload.reportBody || payload.reportBody.trim().length < 10) return "missing_report_body";
  return null;
}

function isChunkPayload(payload: unknown): payload is ReportChunkPayload {
  return Boolean(
    payload &&
      typeof payload === "object" &&
      (payload as { uploadMode?: unknown }).uploadMode === "chunk",
  );
}

function validateChunkPayload(payload: ReportChunkPayload): string | null {
  if (!payload.sessionId || !isUuid(payload.sessionId)) return "invalid_session_id";
  if (!payload.installationId || payload.installationId.trim().length < 8) {
    return "missing_installation_id";
  }
  if (!Number.isInteger(payload.chunkIndex) || payload.chunkIndex < 0) {
    return "invalid_chunk_index";
  }
  if (
    !Number.isInteger(payload.chunkCount) ||
    payload.chunkCount < 1 ||
    payload.chunkCount > MAX_CHUNK_COUNT
  ) {
    return "invalid_chunk_count";
  }
  if (payload.chunkIndex >= payload.chunkCount) return "chunk_index_out_of_range";
  if (
    payload.payloadEncoding &&
    payload.payloadEncoding !== "plain-json" &&
    payload.payloadEncoding !== "gzip-base64-json"
  ) {
    return "invalid_payload_encoding";
  }
  if (typeof payload.chunkText !== "string") return "missing_chunk_text";
  if (payload.chunkText.length > MAX_CHUNK_TEXT_CHARS) return "chunk_too_large";
  return null;
}

async function handleChunkedReport(
  req: Request,
  serviceKey: string,
  payload: ReportChunkPayload,
): Promise<Response> {
  const validationError = validateChunkPayload(payload);
  if (validationError) return jsonResponse({ error: validationError }, 400);

  await cleanupExpiredChunks(serviceKey);

  const stored = await upsertChunk(serviceKey, payload);
  if (!stored.ok) return jsonResponse({ error: "chunk_store_failed", detail: stored.error }, 500);

  if (!payload.finalChunk) {
    return jsonResponse({
      ok: true,
      complete: false,
      sessionId: payload.sessionId,
      received: payload.chunkIndex + 1,
      chunkCount: payload.chunkCount,
    });
  }

  const chunks = await fetchChunks(serviceKey, payload.sessionId, payload.chunkCount);
  if (!chunks.ok) {
    return jsonResponse({ error: "chunk_fetch_failed", detail: chunks.error }, 500);
  }

  if (chunks.rows.length !== payload.chunkCount) {
    return jsonResponse(
      {
        error: "chunks_incomplete",
        received: chunks.rows.length,
        chunkCount: payload.chunkCount,
      },
      409,
    );
  }

  const chunkedPayload = chunks.rows.map((chunk) => chunk.chunk_text).join("");
  if (chunkedPayload.length > MAX_CHUNKED_PAYLOAD_CHARS) {
    await deleteChunks(serviceKey, payload.sessionId);
    return jsonResponse({ error: "chunked_payload_too_large" }, 413);
  }

  const decoded = await decodeChunkedPayload(chunkedPayload, payload.payloadEncoding);
  if (!decoded.ok) {
    await deleteChunks(serviceKey, payload.sessionId);
    return jsonResponse({ error: decoded.error }, 400);
  }

  let reportPayload: ReportPayload;
  try {
    reportPayload = JSON.parse(decoded.text);
  } catch (_error) {
    await deleteChunks(serviceKey, payload.sessionId);
    return jsonResponse({ error: "invalid_chunked_json" }, 400);
  }

  const reportValidationError = validatePayload(reportPayload);
  if (reportValidationError) {
    await deleteChunks(serviceKey, payload.sessionId);
    return jsonResponse({ error: reportValidationError }, 400);
  }

  const response = await handleCompleteReport(req, serviceKey, reportPayload, {
    chunked: true,
    chunkSessionId: payload.sessionId,
  });
  if (response.ok) await deleteChunks(serviceKey, payload.sessionId);
  return response;
}

async function decodeChunkedPayload(
  chunkedPayload: string,
  encoding: ReportChunkPayload["payloadEncoding"],
): Promise<{ ok: true; text: string } | { ok: false; error: string }> {
  if (!encoding || encoding === "plain-json") {
    return { ok: true, text: chunkedPayload };
  }

  try {
    const compressed = base64ToBytes(chunkedPayload);
    const decompressed = await new Response(
      new Blob([compressed]).stream().pipeThrough(new DecompressionStream("gzip")),
    ).text();
    if (decompressed.length > MAX_DECOMPRESSED_PAYLOAD_CHARS) {
      return { ok: false, error: "decompressed_payload_too_large" };
    }
    return { ok: true, text: decompressed };
  } catch (_error) {
    return { ok: false, error: "invalid_gzip_payload" };
  }
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function serviceHeaders(serviceKey: string): HeadersInit {
  const headers: Record<string, string> = {
    ...jsonHeaders,
    apikey: serviceKey,
  };

  if (serviceKey.split(".").length === 3) {
    headers.Authorization = `Bearer ${serviceKey}`;
  }
  return headers;
}

async function countReportsForInstallation(
  serviceKey: string,
  installationId: string,
): Promise<number> {
  const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_reports`);
  url.searchParams.set("select", "id");
  url.searchParams.set("installation_id", `eq.${installationId}`);
  url.searchParams.set("created_at", `gte.${since}`);
  url.searchParams.set("limit", String(MAX_REPORTS_PER_INSTALLATION_PER_DAY));

  const response = await fetch(url, {
    method: "GET",
    headers: serviceHeaders(serviceKey),
  });
  if (!response.ok) return MAX_REPORTS_PER_INSTALLATION_PER_DAY;
  const rows = await response.json();
  return Array.isArray(rows) ? rows.length : MAX_REPORTS_PER_INSTALLATION_PER_DAY;
}

async function insertReport(serviceKey: string, row: Record<string, unknown>): Promise<InsertResult> {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_reports`);
  url.searchParams.set("select", "id");
  const response = await fetch(url, {
    method: "POST",
    headers: {
      ...serviceHeaders(serviceKey),
      Prefer: "return=representation",
    },
    body: JSON.stringify(row),
  });

  const text = await response.text();
  if (!response.ok) return { ok: false, error: text };

  const rows = JSON.parse(text);
  const id = Array.isArray(rows) ? rows[0]?.id : rows?.id;
  if (!id) return { ok: false, error: "missing_inserted_id" };
  return { ok: true, id };
}

async function upsertChunk(
  serviceKey: string,
  payload: ReportChunkPayload,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_report_chunks`);
  url.searchParams.set("on_conflict", "session_id,chunk_index");
  const response = await fetch(url, {
    method: "POST",
    headers: {
      ...serviceHeaders(serviceKey),
      Prefer: "resolution=merge-duplicates",
    },
    body: JSON.stringify({
      session_id: payload.sessionId,
      installation_id: payload.installationId.trim(),
      chunk_index: payload.chunkIndex,
      chunk_count: payload.chunkCount,
      chunk_text: payload.chunkText,
    }),
  });

  if (response.ok) return { ok: true };
  return { ok: false, error: await response.text() };
}

async function fetchChunks(
  serviceKey: string,
  sessionId: string,
  chunkCount: number,
): Promise<{ ok: true; rows: ChunkRow[] } | { ok: false; error: string }> {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_report_chunks`);
  url.searchParams.set("select", "chunk_index,chunk_text");
  url.searchParams.set("session_id", `eq.${sessionId}`);
  url.searchParams.set("order", "chunk_index.asc");
  url.searchParams.set("limit", String(chunkCount));

  const response = await fetch(url, {
    method: "GET",
    headers: serviceHeaders(serviceKey),
  });
  const text = await response.text();
  if (!response.ok) return { ok: false, error: text };

  const rows = JSON.parse(text);
  if (!Array.isArray(rows)) return { ok: false, error: "invalid_chunk_rows" };
  return { ok: true, rows };
}

async function deleteChunks(serviceKey: string, sessionId: string): Promise<void> {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_report_chunks`);
  url.searchParams.set("session_id", `eq.${sessionId}`);
  await fetch(url, {
    method: "DELETE",
    headers: serviceHeaders(serviceKey),
  });
}

async function cleanupExpiredChunks(serviceKey: string): Promise<void> {
  const cutoff = new Date(Date.now() - CHUNK_RETENTION_HOURS * 60 * 60 * 1000).toISOString();
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_report_chunks`);
  url.searchParams.set("created_at", `lt.${cutoff}`);
  await fetch(url, {
    method: "DELETE",
    headers: serviceHeaders(serviceKey),
  });
}

async function updateGithubIssue(
  serviceKey: string,
  id: string,
  githubIssueUrl: string,
  githubIssueNumber?: number,
) {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_reports`);
  url.searchParams.set("id", `eq.${id}`);
  await fetch(url, {
    method: "PATCH",
    headers: serviceHeaders(serviceKey),
    body: JSON.stringify({
      github_issue_url: githubIssueUrl,
      github_issue_number: githubIssueNumber ?? null,
      status: "linked",
      updated_at: new Date().toISOString(),
    }),
  });
}

async function updateReportLogStorage(
  serviceKey: string,
  id: string,
  upload: Extract<ReportFileUploadResult, { ok: true }>,
) {
  const url = new URL(`${SUPABASE_URL}/rest/v1/impulse_problem_reports`);
  url.searchParams.set("id", `eq.${id}`);
  await fetch(url, {
    method: "PATCH",
    headers: serviceHeaders(serviceKey),
    body: JSON.stringify({
      log_storage_bucket: upload.bucket,
      log_storage_path: upload.path,
      log_storage_size_bytes: upload.sizeBytes,
      updated_at: new Date().toISOString(),
    }),
  });
}

async function uploadReportFile(
  serviceKey: string,
  reportId: string,
  payload: ReportPayload,
  reportBody: string,
): Promise<ReportFileUploadResult> {
  const path = buildReportStoragePath(reportId, payload.logFileName);
  const body = buildReportStorageBody(reportId, payload, reportBody);
  const bytes = new TextEncoder().encode(body);
  const encodedPath = path.split("/").map(encodeURIComponent).join("/");
  const url = `${SUPABASE_URL}/storage/v1/object/${LOG_STORAGE_BUCKET}/${encodedPath}`;

  const headers: Record<string, string> = {
    apikey: serviceKey,
    "Content-Type": "text/plain",
    "Cache-Control": "no-store",
    "x-upsert": "true",
  };
  if (serviceKey.split(".").length === 3) {
    headers.Authorization = `Bearer ${serviceKey}`;
  }

  const response = await fetch(url, {
    method: "POST",
    headers,
    body: bytes,
  });

  if (!response.ok) {
    return { ok: false, error: await response.text() };
  }

  return {
    ok: true,
    bucket: LOG_STORAGE_BUCKET,
    path,
    sizeBytes: bytes.byteLength,
  };
}

function buildReportStoragePath(reportId: string, logFileName: string | undefined): string {
  const date = new Date().toISOString().slice(0, 10);
  const primaryName = (logFileName ?? "problem-report.log").split(";")[0]?.trim();
  const fileName = sanitizeStorageFileName(primaryName || "problem-report.log")
    .replace(/\.log$/i, ".txt");
  return `${date}/${reportId}/${fileName.endsWith(".txt") ? fileName : `${fileName}.txt`}`;
}

function buildReportStorageBody(
  reportId: string,
  payload: ReportPayload,
  reportBody: string,
): string {
  const body = [
    `Supabase report id: ${reportId}`,
    `Installation id: ${payload.installationId}`,
    `Generated at: ${payload.generatedAt ?? ""}`,
    `Client timezone: ${payload.timeZone ?? ""}`,
    `Log file name: ${payload.logFileName ?? ""}`,
    "",
    reportBody,
  ].join("\n");

  if (body.length <= MAX_REPORT_STORAGE_CHARS) return body;
  return `${body.slice(0, MAX_REPORT_STORAGE_CHARS)}\n\n[arquivo truncado no Storage]`;
}

function sanitizeStorageFileName(value: string): string {
  return value
    .trim()
    .replace(/[^A-Za-z0-9_.-]/g, "_")
    .replace(/^_+/, "")
    .slice(0, 120) || "problem-report.txt";
}

async function maybeCreateGithubIssue(title: string | undefined, body: string, reportId: string) {
  const token = Deno.env.get("GITHUB_TOKEN");
  const repo = Deno.env.get("GITHUB_REPO");
  if (!token || !repo) {
    return { githubIssueUrl: null, githubIssueNumber: null };
  }

  const issueBody =
    `${body.slice(0, 12000)}\n\n---\nSupabase report id: ${reportId}`;
  const response = await fetch(`https://api.github.com/repos/${repo}/issues`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
      "User-Agent": "impulse-problem-reporter",
    },
    body: JSON.stringify({
      title: truncate(title || "[Relato] Problema no Impulse", 120),
      body: issueBody,
      labels: ["impulse-report"],
    }),
  });

  if (!response.ok) {
    return { githubIssueUrl: null, githubIssueNumber: null };
  }

  const json = await response.json();
  return {
    githubIssueUrl: json.html_url ?? null,
    githubIssueNumber: json.number ?? null,
  };
}

function getServiceKey(): string {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (legacy) return legacy;

  const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (!secretKeys) return "";
  try {
    return JSON.parse(secretKeys).default ?? "";
  } catch (_error) {
    return "";
  }
}

function isAllowedClientKey(apiKey: string | null): boolean {
  if (!apiKey) return false;
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  if (anon && apiKey === anon) return true;

  const publishableKeys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (!publishableKeys) return false;
  try {
    const values = Object.values(JSON.parse(publishableKeys));
    return values.includes(apiKey);
  } catch (_error) {
    return false;
  }
}

async function hashIp(forwardedFor: string | null): Promise<string | null> {
  const ip = forwardedFor?.split(",")[0]?.trim();
  const salt = Deno.env.get("REPORT_IP_HASH_SALT") ?? "";
  if (!ip || !salt) return null;

  const bytes = new TextEncoder().encode(`${salt}:${ip}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function truncate(value: string, maxChars: number): string {
  if (value.length <= maxChars) return value;
  return value.slice(0, maxChars);
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value);
}

function numberOrZero(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.trunc(value) : 0;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}
