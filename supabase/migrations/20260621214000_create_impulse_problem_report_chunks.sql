create table if not exists public.impulse_problem_report_chunks (
  session_id uuid not null,
  installation_id text not null,
  chunk_index integer not null check (chunk_index >= 0),
  chunk_count integer not null check (chunk_count between 1 and 4000),
  chunk_text text not null check (char_length(chunk_text) <= 700),
  created_at timestamptz not null default now(),
  primary key (session_id, chunk_index)
);

create index if not exists impulse_problem_report_chunks_created_at_idx
  on public.impulse_problem_report_chunks (created_at);

create index if not exists impulse_problem_report_chunks_installation_created_idx
  on public.impulse_problem_report_chunks (installation_id, created_at desc);

alter table public.impulse_problem_report_chunks enable row level security;

revoke all on public.impulse_problem_report_chunks from anon, authenticated;
grant select, insert, update, delete on public.impulse_problem_report_chunks to service_role;

drop policy if exists "service_role manages impulse problem report chunks"
  on public.impulse_problem_report_chunks;

create policy "service_role manages impulse problem report chunks"
  on public.impulse_problem_report_chunks
  for all
  to service_role
  using (true)
  with check (true);
