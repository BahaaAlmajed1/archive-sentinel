create table admin_users (
    id uuid primary key,
    username varchar(120) not null unique,
    password_hash varchar(255) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table app_settings (
    id bigint primary key,
    setup_completed boolean not null,
    execution_mode varchar(32) not null default 'HOST_NATIVE',
    tdarr_mode varchar(32) not null,
    tdarr_base_url varchar(500) not null,
    managed_tdarr_enabled boolean not null,
    archive_root varchar(1000) not null,
    optimized_root varchar(1000) not null,
    staging_enabled boolean not null,
    staging_root varchar(1000),
    default_retention_days integer not null,
    default_deletion_mode varchar(32) not null,
    default_output_mode varchar(32) not null,
    report_interval_hours integer not null,
    report_recipients text not null,
    smtp_host varchar(500) not null default '',
    smtp_port integer not null default 25,
    smtp_username varchar(500) not null default '',
    smtp_password varchar(500) not null default '',
    smtp_auth boolean not null default false,
    smtp_start_tls boolean not null default false,
    smtp_from varchar(500) not null default 'archive-sentinel@localhost',
    scan_threads integer not null,
    analysis_threads integer not null,
    validation_threads integer not null,
    file_ops_threads integer not null,
    tdarr_submission_concurrency integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table storage_roots (
    id uuid primary key,
    label varchar(200) not null,
    path varchar(1000) not null unique,
    enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table policy_targets (
    id uuid primary key,
    path varchar(1000) not null,
    target_type varchar(32) not null,
    excluded boolean not null,
    recursive boolean not null,
    excluded_extensions text,
    retention_days integer,
    deletion_mode varchar(32),
    output_mode varchar(32),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table media_files (
    id uuid primary key,
    storage_root_id uuid not null references storage_roots(id),
    original_path varchar(1500) not null unique,
    relative_path varchar(1200) not null,
    size_bytes bigint not null,
    last_accessed_at timestamptz,
    status varchar(64) not null,
    metadata_json text,
    validation_json text,
    candidate_path varchar(1500),
    optimized_path varchar(1500),
    archived_path varchar(1500),
    archived_at timestamptz,
    retention_until timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table optimization_runs (
    id uuid primary key,
    status varchar(64) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    total_files integer not null,
    completed_files integer not null,
    failed_files integer not null,
    total_input_bytes bigint not null,
    total_output_bytes bigint not null,
    report_path varchar(1500),
    log_path varchar(1500)
);

create table optimization_run_items (
    id uuid primary key,
    run_id uuid not null references optimization_runs(id),
    media_file_id uuid references media_files(id),
    status varchar(64) not null,
    original_path varchar(1500) not null,
    candidate_path varchar(1500),
    optimized_path varchar(1500),
    archived_path varchar(1500),
    input_bytes bigint not null,
    output_bytes bigint not null,
    saved_bytes bigint not null,
    error_message text,
    validation_json text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table precheck_runs (
    id uuid primary key,
    status varchar(64) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    root_ids text not null,
    total_files integer not null,
    total_bytes bigint not null,
    estimated_output_bytes bigint not null,
    estimated_savings_bytes bigint not null,
    report_path varchar(1500),
    log_path varchar(1500),
    error_message text
);

create table audit_events (
    id uuid primary key,
    media_file_id uuid,
    event_type varchar(120) not null,
    message text not null,
    created_at timestamptz not null
);

create index idx_media_files_status on media_files(status);
create index idx_media_files_storage_root on media_files(storage_root_id);
create index idx_run_items_run on optimization_run_items(run_id);
create index idx_precheck_runs_status on precheck_runs(status);
create index idx_audit_events_media_file on audit_events(media_file_id);
