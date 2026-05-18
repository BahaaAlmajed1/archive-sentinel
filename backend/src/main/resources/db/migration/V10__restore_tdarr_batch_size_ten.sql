update app_settings
set tdarr_submission_concurrency = 10
where tdarr_submission_concurrency < 10;
