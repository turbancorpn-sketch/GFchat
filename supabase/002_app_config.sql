-- GFchat: force-update config.
-- Supabase Dashboard -> SQL Editor me paste karke Run karo (001_pairing.sql ke baad).

create table if not exists public.app_config (
  id int primary key default 1,
  min_version_code int not null default 1,
  download_url text not null default 'https://github.com/turbancorpn-sketch/GFchat/releases/download/latest/GFchat.apk',
  constraint app_config_singleton check (id = 1)
);

insert into public.app_config (id, min_version_code, download_url)
  values (1, 1, 'https://github.com/turbancorpn-sketch/GFchat/releases/download/latest/GFchat.apk')
  on conflict (id) do nothing;

alter table public.app_config enable row level security;

-- Sabko (login se pehle bhi) padhne dena, kisi ko likhne nahi dena.
drop policy if exists "app_config public read" on public.app_config;
create policy "app_config public read" on public.app_config
  for select to anon, authenticated
  using (true);

-- Naya version force karna ho: isse Supabase Table Editor me chalao, min_version_code badha do.
-- update public.app_config set min_version_code = 2 where id = 1;
