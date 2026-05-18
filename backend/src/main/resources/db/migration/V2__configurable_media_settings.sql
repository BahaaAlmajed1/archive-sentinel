alter table app_settings
    add column precheck_extension_options text not null default 'mkv,mp4,mov,avi,m4v,webm,mpg,mpeg,wmv,m2ts,ts',
    add column enabled_extensions text not null default 'mkv,mp4,mov,avi,m4v,webm,mpg,mpeg,wmv,m2ts,ts',
    add column estimated_output_ratio_percent integer not null default 65,
    add column output_container_extension varchar(32) not null default 'mkv',
    add column duration_tolerance_seconds double precision not null default 0.5;
