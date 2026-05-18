alter table storage_roots
    add column last_scanned_at timestamptz,
    add column latest_precheck_run_id uuid,
    add column auto_rescan_enabled boolean not null default false;

alter table app_settings
    add column background_scan_refresh_minutes integer not null default 15;
