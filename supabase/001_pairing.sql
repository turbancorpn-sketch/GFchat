-- GFchat Stage 2A: sirf 2 users ka pairing.
-- Supabase Dashboard -> SQL Editor me poora paste karke Run karo.

create table if not exists public.couples (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users (id) on delete cascade,
  user_b uuid references auth.users (id) on delete cascade,
  pair_code text,
  pair_code_expires timestamptz,
  created_at timestamptz not null default now(),
  constraint couples_distinct check (user_b is null or user_a <> user_b),
  constraint couples_user_a_unique unique (user_a),
  constraint couples_user_b_unique unique (user_b)
);

alter table public.couples enable row level security;

-- Sirf couple ke 2 log apni row dekh sakte hain. Koi direct insert/update/delete nahi.
drop policy if exists "couple members can read" on public.couples;
create policy "couple members can read" on public.couples
  for select to authenticated
  using (auth.uid() = user_a or auth.uid() = user_b);

-- Pairing code banao (15 minute valid). Pehle se paired ho to error.
create or replace function public.create_pair_code()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  c public.couples;
  new_code text;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  select * into c from public.couples where user_a = uid or user_b = uid;
  if found and c.user_b is not null then raise exception 'ALREADY_PAIRED'; end if;

  new_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));

  if found then
    update public.couples
      set pair_code = new_code, pair_code_expires = now() + interval '15 minutes'
      where id = c.id;
  else
    insert into public.couples (user_a, pair_code, pair_code_expires)
      values (uid, new_code, now() + interval '15 minutes');
  end if;

  return new_code;
end;
$$;

-- Partner ka code daalke judo. Code ek hi baar chalta hai.
create or replace function public.join_pair_code(code text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  c public.couples;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  select * into c from public.couples where user_a = uid or user_b = uid;
  if found and c.user_b is not null then raise exception 'ALREADY_PAIRED'; end if;

  -- Agar galti se apna code bana liya tha, wo waiting row hata do.
  delete from public.couples where user_a = uid and user_b is null;

  update public.couples
    set user_b = uid, pair_code = null, pair_code_expires = null
    where pair_code = upper(trim(code))
      and user_b is null
      and pair_code_expires > now()
      and user_a <> uid;

  if not found then raise exception 'CODE_INVALID'; end if;
end;
$$;

-- App ko batata hai: none / waiting (code ke saath) / paired.
create or replace function public.pair_status()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  c public.couples;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

  select * into c from public.couples where user_a = uid or user_b = uid;
  if not found then
    return jsonb_build_object('state', 'none');
  elsif c.user_b is not null then
    return jsonb_build_object('state', 'paired');
  elsif c.pair_code_expires > now() then
    return jsonb_build_object('state', 'waiting', 'code', c.pair_code);
  else
    return jsonb_build_object('state', 'none');
  end if;
end;
$$;

revoke all on function public.create_pair_code() from public, anon;
revoke all on function public.join_pair_code(text) from public, anon;
revoke all on function public.pair_status() from public, anon;
grant execute on function public.create_pair_code() to authenticated;
grant execute on function public.join_pair_code(text) to authenticated;
grant execute on function public.pair_status() to authenticated;
