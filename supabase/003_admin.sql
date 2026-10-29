-- GFchat: admin role + force-update control.
-- Supabase Dashboard -> SQL Editor me paste karke Run karo (001 aur 002 ke baad).
-- "Admin" wahi account hai jisne sabse pehle pairing code banaya tha (couples.user_a).

-- pair_status ab is_admin bhi batata hai.
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
    return jsonb_build_object('state', 'paired', 'is_admin', (c.user_a = uid));
  elsif c.pair_code_expires > now() then
    return jsonb_build_object('state', 'waiting', 'code', c.pair_code);
  else
    return jsonb_build_object('state', 'none');
  end if;
end;
$$;

-- Sirf admin (user_a) naya minimum version set kar sakta hai.
create or replace function public.admin_set_min_version(new_min int, new_url text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  is_admin boolean;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if new_min < 1 then raise exception 'INVALID_VERSION'; end if;

  select exists(select 1 from public.couples where user_a = uid and user_b is not null) into is_admin;
  if not is_admin then raise exception 'NOT_ADMIN'; end if;

  update public.app_config
    set min_version_code = new_min,
        download_url = coalesce(nullif(trim(new_url), ''), download_url)
    where id = 1;
end;
$$;

revoke all on function public.admin_set_min_version(int, text) from public, anon;
grant execute on function public.admin_set_min_version(int, text) to authenticated;
