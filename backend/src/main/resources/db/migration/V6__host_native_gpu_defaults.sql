update app_settings
set tdarr_transcode_arguments = ',-map 0 -map_metadata 0 -map_chapters 0 -c:v hevc_nvenc -preset p5 -cq 28 -c:a copy -c:s copy'
where tdarr_transcode_arguments = ',-map 0 -map_metadata 0 -map_chapters 0 -c:v libx265 -preset fast -crf 28 -c:a copy -c:s copy';
