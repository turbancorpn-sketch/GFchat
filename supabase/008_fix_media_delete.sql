-- GFchat/Scooter: fix "chat clear nahi ho rahi" / "unsend nahi ho raha" bug.
--
-- Root cause: delete_message/clear_chat tried to `delete from storage.objects`
-- directly inside a SECURITY DEFINER function. Supabase now blocks that
-- ("Direct deletion from storage tables is not allowed. Use the Storage API
-- instead."), which made the whole transaction fail -> nothing got deleted,
-- not even the message rows.
--
-- Fix: these functions no longer touch storage.objects. They just return the
-- media path(s) that need deleting, and the phone deletes them itself via the
-- Storage API (DELETE /storage/v1/object/media) using its own login — which
-- needs this new storage DELETE policy, scoped to the user's own couple.

drop function if exists public.delete_message(bigint);
drop function if exists public.clear_chat();

drop policy if exists "couple can delete own media" on storage.objects;
create policy "couple can delete own media"
  on storage.objects
  for delete
  to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = (my_couple_id_safe())::text);

create or replace function public.delete_message(message_id bigint)
returns jsonb
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
    return jsonb_build_object('media_path', old_body);
  end if;
  return jsonb_build_object('media_path', null);
end;
$$;

create or replace function public.clear_chat()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  cid uuid := private_my_couple_id();
  paths jsonb;
begin
  select coalesce(jsonb_agg(body), '[]'::jsonb) into paths
    from public.messages
    where couple_id = cid and body is not null and kind in ('photo', 'video');

  delete from public.messages where couple_id = cid;

  return jsonb_build_object('media_paths', paths);
end;
$$;

revoke all on function public.delete_message(bigint) from public, anon;
revoke all on function public.clear_chat() from public, anon;
grant execute on function public.delete_message(bigint) to authenticated;
grant execute on function public.clear_chat() to authenticated;
