-- GFchat/Closer: private storage bucket for photo/video messages.
-- Supabase Dashboard -> SQL Editor me paste karke Run karo (001-004 ke baad).

insert into storage.buckets (id, name, public)
  values ('media', 'media', false)
  on conflict (id) do nothing;

-- Safe version: returns null instead of raising, so it never breaks unrelated storage queries.
create or replace function public.my_couple_id_safe()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  cid uuid;
begin
  select id into cid from public.couples
    where (user_a = auth.uid() or user_b = auth.uid()) and user_b is not null;
  return cid;
exception when others then
  return null;
end;
$$;

-- Public wrapper the app calls once to know its upload folder (raises if not paired).
create or replace function public.my_couple_id()
returns uuid
language plpgsql
security definer
set search_path = public
as $$
begin
  return private_my_couple_id();
end;
$$;

revoke all on function public.my_couple_id() from public, anon;
grant execute on function public.my_couple_id() to authenticated;

drop policy if exists "couple can read own media" on storage.objects;
create policy "couple can read own media" on storage.objects
  for select to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = my_couple_id_safe()::text);

drop policy if exists "couple can upload own media" on storage.objects;
create policy "couple can upload own media" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'media' and (storage.foldername(name))[1] = my_couple_id_safe()::text);
