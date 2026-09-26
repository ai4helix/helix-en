INSERT INTO `t_resource` (`id`, `user_id`, `name`, `code`, `url`, `parent_id`, `des`, `birth`, `icon`, `status`)
VALUES (82, NULL, 'Workbench', 'WORKBENCH', '/workbench', 0, 'Unified four-layer workbench: Indicators→Rules→Decision Flow→Run', NOW(), 'HomeFilled', 1);

INSERT INTO `t_role_resource_rel` (`role_id`, `resource_id`) VALUES
  (1, 82),
  (2, 82),
  (4, 82),
  (8, 82);
