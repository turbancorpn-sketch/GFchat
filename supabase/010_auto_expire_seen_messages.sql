-- pg_cron schedule job chalane ke liye.
create extension if not exists pg_cron;

-- Jab "seen" (read_at) ke 30 min expire ho jaate hain, usi deleted-message
-- shape me convert kar dete hain jo manual delete_message() banata hai
-- (deleted_at set, body null) — taaki client ko koi alag se handling na karni pade,
-- existing realtime/poll pipeline khud hi "message deleted" dikha dega.
-- Photo/video ka storage path "media_cleanup_queue" me daal dete hain, kyunki
-- SQL se seedha storage.objects delete allowed nahi hai (Storage API se hi hota hai) —
-- app agli baar khulne/poll hone par ye queue clear karke actual file delete karegi.
create table if not exists public.media_cleanup_queue (
  id bigint generated always as identity primary key,
  couple_id uuid not null,
  media_path text not null,
  created_at timestamptz not null default now()
);

alter table public.media_cleanup_queue enable row level security;

create policy "couple can read own media cleanup queue"
  on public.media_cleanup_queue for select
  using (couple_id = private_my_couple_id());

create or replace function public.expire_seen_messages()
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
begin
  insert into public.media_cleanup_queue (couple_id, media_path)
  select couple_id, body
  from public.messages
  where read_at is not null
    and read_at < now() - interval '30 minutes'
    and deleted_at is null
    and kind in ('photo', 'video')
    and body is not null;

  update public.messages
    set deleted_at = now(), updated_at = now(), body = null
    where read_at is not null
      and read_at < now() - interval '30 minutes'
      and deleted_at is null;
end;
$$;

-- RPCs taaki Android app apne couple ke pending media-cleanup paths fetch/clear kar sake.
create or replace function public.fetch_media_cleanup()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  cid uuid := private_my_couple_id();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object('id', q.id, 'media_path', q.media_path))
    from public.media_cleanup_queue q
    where q.couple_id = cid
  ), '[]'::jsonb);
end;
$$;

create or replace function public.ack_media_cleanup(ids bigint[])
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  cid uuid := private_my_couple_id();
begin
  delete from public.media_cleanup_queue
    where couple_id = cid and id = any(ids);
end;
$$;

-- har 1 minute me expired-seen messages check/clean karta hai.
select cron.schedule(
  'expire-seen-messages',
  '* * * * *',
  $$select public.expire_seen_messages();$$
);
