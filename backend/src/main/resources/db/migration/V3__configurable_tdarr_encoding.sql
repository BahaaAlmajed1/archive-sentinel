alter table app_settings
    add column tdarr_codecs_to_exclude text not null default 'hevc',
    add column tdarr_transcode_arguments text not null default ',-map 0 -map_metadata 0 -map_chapters 0 -c:v libx265 -preset fast -crf 28 -c:a copy -c:s copy';
