alter table media_files
    add column last_modified_at timestamptz;

alter table precheck_runs
    add column scanned_files integer not null default 0,
    add column progress_message varchar(500) not null default 'Waiting to start';

create index idx_media_files_original_path on media_files(original_path);
