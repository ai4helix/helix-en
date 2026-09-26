INSERT INTO `t_resource` (`id`, `user_id`, `name`, `code`, `url`, `parent_id`, `des`, `birth`, `icon`, `status`)
VALUES (83, NULL, 'User Guide', 'MENU_GUIDE', '/guide', 0, 'Quick start: build a decision in four steps, canvas operations, trial runs and batch run guide', NOW(), 'QuestionFilled', 1);

INSERT INTO `t_role_resource_rel` (`role_id`, `resource_id`) VALUES
  (1, 83),
  (2, 83),
  (4, 83),
  (8, 83);
