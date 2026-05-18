alter table media_files
    add column retention_deletion_mode varchar(32),
    add column ever_optimized boolean not null default false,
    add column last_optimized_at timestamptz;

create table precheck_run_items (
    id uuid primary key,
    precheck_run_id uuid not null references precheck_runs(id),
    media_file_id uuid not null references media_files(id),
    folder_path varchar(1200) not null,
    selected boolean not null,
    default_selected boolean not null,
    retention_days integer not null,
    deletion_mode varchar(32) not null,
    output_mode varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table automation_rules (
    id uuid primary key,
    storage_root_id uuid not null unique references storage_roots(id),
    enabled boolean not null,
    interval_minutes integer not null,
    last_run_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_precheck_run_items_run on precheck_run_items(precheck_run_id);
create index idx_precheck_run_items_folder on precheck_run_items(precheck_run_id, folder_path);
create index idx_precheck_run_items_selected on precheck_run_items(precheck_run_id, selected);
