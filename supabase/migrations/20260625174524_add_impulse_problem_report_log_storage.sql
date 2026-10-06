insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'impulse-problem-report-logs',
  'impulse-problem-report-logs',
  false,
  5000000,
  array['text/plain']
)
on conflict (id) do update
set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types,
  updated_at = now();

alter table public.impulse_problem_reports
  add column if not exists log_storage_bucket text,
  add column if not exists log_storage_path text,
  add column if not exists log_storage_size_bytes integer not null default 0;
