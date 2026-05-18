alter table app_settings
    add column if not exists media_ffmpeg_path varchar(1500) not null default '',
    add column if not exists media_ffprobe_path varchar(1500) not null default '';
