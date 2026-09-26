CREATE TABLE `t_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `organ_id` bigint(20) DEFAULT NULL COMMENT '组织编号',
  `user_type` tinyint(4) NOT NULL DEFAULT '2' COMMENT '用户类型：1 平台管理用户 2 SaaS 机构用户',
  `employee_id` varchar(100) DEFAULT NULL COMMENT '员工编号',
  `account` varchar(100) NOT NULL COMMENT '账户（手机或邮箱）',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt）',
  `nick_name` varchar(100) NOT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `cellphone` varchar(100) DEFAULT NULL COMMENT '手机号',
  `qq` varchar(100) DEFAULT NULL COMMENT 'QQ',
  `latest_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `latest_ip` varchar(100) DEFAULT NULL COMMENT '最后登录 IP',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 禁用 1 启用 -1 假删',
  `birth` datetime DEFAULT NULL COMMENT '创建时间',
  `author` varchar(100) DEFAULT NULL COMMENT '创建者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`),
  KEY `idx_user_organ` (`organ_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COMMENT='用户';
CREATE TABLE `t_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `organ_id` bigint(20) DEFAULT NULL COMMENT '组织编号',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `role_code` varchar(100) DEFAULT NULL COMMENT '角色代号',
  `role_desc` varchar(100) DEFAULT NULL COMMENT '角色描述',
  `author` varchar(100) DEFAULT NULL COMMENT '创建者',
  `birth` datetime DEFAULT NULL COMMENT '创建时间',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 禁用 1 启用',
  PRIMARY KEY (`id`),
  KEY `idx_role_organ` (`organ_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COMMENT='角色';
CREATE TABLE `t_user_role_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户编号',
  `role_id` bigint(20) DEFAULT NULL COMMENT '角色编号',
  `organ_id` bigint(20) DEFAULT NULL COMMENT '公司编号',
  `status` int(10) DEFAULT '1' COMMENT '1 启用 0 停用 -1 删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ur` (`user_id`,`role_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';
CREATE TABLE `t_resource` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT '分配人',
  `name` varchar(100) NOT NULL COMMENT '资源名称',
  `code` varchar(100) NOT NULL COMMENT '资源代号',
  `url` varchar(100) NOT NULL COMMENT '资源路径',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父节点',
  `des` varchar(100) DEFAULT NULL COMMENT '资源描述',
  `birth` datetime DEFAULT NULL COMMENT '创建时间',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态',
  PRIMARY KEY (`id`),
  KEY `idx_res_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COMMENT='资源（菜单/接口）';
CREATE TABLE `t_role_resource_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `role_id` bigint(20) DEFAULT NULL COMMENT '角色 id',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '资源 id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rr` (`role_id`,`resource_id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COMMENT='角色资源关联';
CREATE TABLE `t_organization` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '组织名称',
  `code` varchar(100) NOT NULL COMMENT '组织代号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `telephone` varchar(100) DEFAULT NULL COMMENT '电话',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 禁用 1 启用',
  `author` varchar(100) DEFAULT NULL COMMENT '创建者',
  `birth` datetime DEFAULT NULL COMMENT '创建时间',
  `token` varchar(100) DEFAULT NULL COMMENT '唯一标识',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COMMENT='组织';
