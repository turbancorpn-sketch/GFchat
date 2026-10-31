-- GFchat/Closer: turn on Supabase Realtime for instant message/tick sync
-- (no more waiting for the next poll). Run after 001-006.
--
-- Messages were previously only reachable via RPC (direct table SELECT was
-- blocked by RLS). This adds a couple-scoped SELECT policy so Realtime can
-- push row changes to each phone, and only to their own couple's rows.

drop policy if exists messages_select_own_couple on public.messages;
create policy messages_select_own_couple
  on public.messages
  for select
  to authenticated
  using (couple_id = private_my_couple_id());

grant select on public.messages to authenticated;

alter publication supabase_realtime add table public.messages;
