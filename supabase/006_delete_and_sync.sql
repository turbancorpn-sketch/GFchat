-- GFchat/Closer: "delete for everyone" + sync fix.
-- Supabase Dashboard -> SQL Editor me paste karke Run karo (001-005 ke baad).
--
-- Bug fix: pehle fetch_messages sirf naye id (after_id) laata tha, isliye kisi
-- purane message ki tick (delivered/read) badalne pe sender ko pata hi nahi chalta tha.
-- Ab updated_at-based cursor hai, jisse ticks aur delete dono sync ho jaate hain.

alter table public.messages add column if not exists updated_at timestamptz not null default now();
alter table public.messages add column if not exists deleted_at timestamptz;

create index if not exists messages_couple_updated_idx on public.messages (couple_id, updated_at);

create or replace function public.send_message(kind text, body text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  cid uuid := private_my_couple_id();
  row public.messages;
begin
  if kind not in ('text', 'photo', 'video') then raise exception 'INVALID_KIND'; end if;
  if kind = 'text' and coalesce(trim(body), '') = '' then raise exception 'EMPTY_MESSAGE'; end if;

  insert into public.messages (couple_id, sender_id, kind, body)
    values (cid, uid, kind, body)
    returning * into row;

  return jsonb_build_object(
    'id', row.id, 'kind', row.kind, 'body', row.body,
    'created_at', row.created_at, 'updated_at', row.updated_at,
    'delivered_at', row.delivered_at, 'read_at', row.read_at,
    'deleted', (row.deleted_at is not null), 'from_me', true
  );
end;
$$;

-- since = last updated_at client has seen (ISO timestamp text, or null for first load).
-- Returns everything changed after that: new messages AND tick/delete updates to old ones.
create or replace function public.fetch_messages(since text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  cid uuid := private_my_couple_id();
  since_ts timestamptz := coalesce(since::timestamptz, '-infinity'::timestamptz);
begin
  update public.messages
    set delivered_at = now(), updated_at = now()
    where couple_id = cid and sender_id <> uid and delivered_at is null;

  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', m.id, 'kind', m.kind, 'body', m.body,
      'created_at', m.created_at, 'updated_at', m.updated_at,
      'delivered_at', m.delivered_at, 'read_at', m.read_at,
      'deleted', (m.deleted_at is not null),
      'from_me', (m.sender_id = uid)
    ) order by m.updated_at, m.id)
    from public.messages m
    where m.couple_id = cid and m.updated_at > since_ts
  ), '[]'::jsonb);
end;
$$;

create or replace function public.mark_read()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  cid uuid := private_my_couple_id();
begin
  update public.messages
    set read_at = now(), delivered_at = coalesce(delivered_at, now()), updated_at = now()
    where couple_id = cid and sender_id <> uid and read_at is null;
end;
$$;

-- "Delete for everyone": sirf bhejne wala apna message delete kar sakta hai,
-- dono phones se hat jaata hai (row rehta hai taaki partner ko "deleted" dikh sake).
create or replace function public.delete_message(message_id bigint)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  cid uuid := private_my_couple_id();
  old_body text;
  old_kind text;
begin
  select body, kind into old_body, old_kind
    from public.messages
    where id = message_id and couple_id = cid and sender_id = uid and deleted_at is null
    for update;

  if not found then raise exception 'NOT_FOUND'; end if;

  update public.messages
    set deleted_at = now(), updated_at = now(), body = null
    where id = message_id;

  if old_kind in ('photo', 'video') and old_body is not null then
    delete from storage.objects where bucket_id = 'media' and name = old_body;
  end if;
end;
$$;

-- Poori chat clear karo (dono phones se, sare messages aur unki media files).
create or replace function public.clear_chat()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  cid uuid := private_my_couple_id();
begin
  delete from storage.objects
    where bucket_id = 'media'
      and name in (
        select body from public.messages
        where couple_id = cid and body is not null and kind in ('photo', 'video')
      );
  delete from public.messages where couple_id = cid;
end;
$$;

revoke all on function public.delete_message(bigint) from public, anon;
revoke all on function public.clear_chat() from public, anon;
grant execute on function public.delete_message(bigint) to authenticated;
grant execute on function public.clear_chat() to authenticated;
