INSERT INTO `t_resource` (`id`, `user_id`, `name`, `code`, `url`, `parent_id`, `des`, `birth`, `icon`, `status`)
VALUES (81, NULL, 'Run Center', 'RUN_CENTER', '/run', 0, 'Unified trial-run entry: Indicators→Rules→Decision Flow→Run', NOW(), 'VideoPlay', 1);

INSERT INTO `t_role_resource_rel` (`role_id`, `resource_id`) VALUES
  (1, 81),
  (2, 81),
  (4, 81),
  (8, 81);
