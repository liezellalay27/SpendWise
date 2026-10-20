create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null check (char_length(trim(display_name)) between 1 and 80),
    currency text not null default 'PHP' check (char_length(currency) = 3),
    timezone text not null default 'Asia/Manila',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.budget_cycles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    name text not null default 'Budget cycle',
    start_date date not null,
    end_date date not null,
    budget_cents bigint not null check (budget_cents > 0),
    low_balance_threshold_cents bigint not null default 500000 check (low_balance_threshold_cents >= 0),
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (end_date > start_date)
);

create unique index if not exists one_active_cycle_per_user
    on public.budget_cycles(user_id) where status = 'ACTIVE';

create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    cycle_id uuid not null references public.budget_cycles(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 120),
    amount_cents bigint not null check (amount_cents > 0),
    category text not null default 'OTHER',
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create table if not exists public.income_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    cycle_id uuid not null references public.budget_cycles(id) on delete cascade,
    source text not null check (char_length(trim(source)) between 1 and 120),
    amount_cents bigint not null check (amount_cents > 0),
    emergency_allocation_percent numeric(5,2) not null default 10
        check (emergency_allocation_percent between 0 and 100),
    emergency_allocation_cents bigint not null default 0 check (emergency_allocation_cents >= 0),
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create table if not exists public.extra_budgets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    cycle_id uuid not null references public.budget_cycles(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 120),
    amount_cents bigint not null check (amount_cents > 0),
    created_at timestamptz not null default now()
);

create table if not exists public.emergency_fund_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    type text not null check (type in ('CONTRIBUTION', 'WITHDRAWAL')),
    amount_cents bigint not null check (amount_cents > 0),
    source text not null check (char_length(trim(source)) between 1 and 120),
    created_at timestamptz not null default now()
);

create table if not exists public.alerts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    cycle_id uuid references public.budget_cycles(id) on delete cascade,
    type text not null,
    severity text not null check (severity in ('INFO', 'WARNING', 'CRITICAL')),
    title text not null,
    message text not null,
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists public.cycle_reports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    cycle_id uuid not null unique references public.budget_cycles(id) on delete cascade,
    total_spent_cents bigint not null default 0,
    total_income_cents bigint not null default 0,
    emergency_contributions_cents bigint not null default 0,
    ending_balance_cents bigint not null default 0,
    report jsonb not null default '{}'::jsonb,
    generated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists budget_cycles_set_updated_at on public.budget_cycles;
create trigger budget_cycles_set_updated_at
before update on public.budget_cycles
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, display_name)
    values (
        new.id,
        coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), split_part(new.email, '@', 1))
    )
    on conflict (id) do update
        set display_name = excluded.display_name;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

insert into public.profiles (id, display_name)
select
    id,
    coalesce(nullif(trim(raw_user_meta_data ->> 'display_name'), ''), split_part(email, '@', 1))
from auth.users
on conflict (id) do nothing;

alter table public.profiles enable row level security;
alter table public.budget_cycles enable row level security;
alter table public.expenses enable row level security;
alter table public.income_transactions enable row level security;
alter table public.extra_budgets enable row level security;
alter table public.emergency_fund_transactions enable row level security;
alter table public.alerts enable row level security;
alter table public.cycle_reports enable row level security;

create policy profiles_owner_policy on public.profiles
    for all using (id = auth.uid()) with check (id = auth.uid());

create policy budget_cycles_owner_policy on public.budget_cycles
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy expenses_owner_policy on public.expenses
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy income_transactions_owner_policy on public.income_transactions
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy extra_budgets_owner_policy on public.extra_budgets
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy emergency_fund_transactions_owner_policy on public.emergency_fund_transactions
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy alerts_owner_policy on public.alerts
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy cycle_reports_owner_policy on public.cycle_reports
    for all using (user_id = auth.uid()) with check (user_id = auth.uid());
