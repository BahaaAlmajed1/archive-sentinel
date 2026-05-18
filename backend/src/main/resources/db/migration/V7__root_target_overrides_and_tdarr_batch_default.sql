alter table storage_roots
    add column optimized_root_override varchar(1000),
    add column archive_root_override varchar(1000);

alter table precheck_run_items
    add column optimized_root_override varchar(1000),
    add column archive_root_override varchar(1000);

alter table optimization_run_items
    add column storage_root_id uuid,
    add column storage_root_label varchar(200),
    add column storage_root_path varchar(1000),
    add column optimized_root varchar(1000),
    add column archive_root varchar(1000);

update app_settings
set tdarr_submission_concurrency = 10
where tdarr_submission_concurrency = 2;
