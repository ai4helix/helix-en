-- =====================================================================
-- Helix EN engine database schema (core tables)
-- =====================================================================
CREATE TABLE `t_engine` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `code` varchar(64) DEFAULT NULL COMMENT 'Engine code (used by external API)',
  `name` varchar(64) DEFAULT NULL COMMENT 'Engine name',
  `description` varchar(500) DEFAULT NULL COMMENT 'Description',
  `status` int(11) DEFAULT '1' COMMENT '0 deleted 1 active',
  `created_time` datetime DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  `created_by` bigint(20) DEFAULT NULL,
  `organ_id` int(11) DEFAULT NULL COMMENT 'Organization id',
  `user_id` int(11) DEFAULT NULL COMMENT 'Last modifier',
  PRIMARY KEY (`id`),
  KEY `idx_engine_organ` (`organ_id`,`status`),
  KEY `idx_engine_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COMMENT='Engine';

CREATE TABLE `t_engine_node` (
  `node_id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Node id',
  `version_id` int(11) DEFAULT NULL COMMENT 'Version id',
  `node_name` varchar(200) NOT NULL,
  `node_code` varchar(200) NOT NULL,
  `node_order` int(11) DEFAULT NULL COMMENT 'Node order',
  `node_type` int(11) DEFAULT NULL COMMENT 'Node type, see NodeType',
  `node_json` text,
  `node_x` decimal(7,2) DEFAULT NULL COMMENT 'X6 canvas X coordinate',
  `node_y` decimal(7,2) DEFAULT NULL COMMENT 'X6 canvas Y coordinate',
  `node_script` text COMMENT 'Node script / remark',
  `next_nodes` text COMMENT 'Downstream node codes, comma separated',
  `params` text COMMENT 'Parameters used by the node',
  `parent_id` int(11) DEFAULT NULL COMMENT 'Upstream node id',
  `created_time` datetime DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_node_version_code` (`version_id`,`node_code`),
  KEY `idx_node_version` (`version_id`,`node_order`)
) ENGINE=InnoDB AUTO_INCREMENT=1592 DEFAULT CHARSET=utf8mb4 COMMENT='Decision flow node';

CREATE TABLE `t_engine_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `engine_id` int(11) DEFAULT NULL COMMENT 'Engine id',
  `version` int(11) NOT NULL COMMENT 'Major version',
  `sub_version` int(5) DEFAULT '1' COMMENT 'Sub version',
  `boot_state` smallint(1) NOT NULL DEFAULT '0' COMMENT 'Deployed: 0 not deployed 1 running',
  `status` smallint(1) DEFAULT '1' COMMENT '0 recycle bin 1 normal 2 purged',
  `layout` smallint(1) DEFAULT '0' COMMENT 'Layout: 0 custom 1/2 reserved',
  `user_id` int(11) DEFAULT NULL COMMENT 'Creator',
  `created_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_version_engine` (`engine_id`,`status`),
  KEY `idx_version_boot` (`engine_id`,`boot_state`)
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8mb4 COMMENT='Engine version (multi-scenario, multi-version)';

CREATE TABLE `t_field` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Field id',
  `field_en` varchar(100) NOT NULL COMMENT 'Field English name',
  `field_cn` varchar(500) DEFAULT NULL,
  `field_typeid` int(11) NOT NULL COMMENT 'Field type id',
  `value_type` int(4) DEFAULT NULL COMMENT '0 unset 1 numeric 2 string 3 enum 4 decimal',
  `value_scope` varchar(255) DEFAULT NULL COMMENT 'Field constraint scope',
  `is_derivative` int(4) NOT NULL DEFAULT '0' COMMENT 'Whether derived field',
  `is_output` int(4) NOT NULL DEFAULT '0' COMMENT 'Whether output field',
  `is_common` int(4) NOT NULL DEFAULT '0' COMMENT 'Whether organization-common field',
  `formula` text COMMENT 'Derivation formula',
  `formula_show` text COMMENT 'Formula display text',
  `used_fieldid` varchar(200) DEFAULT NULL COMMENT 'Referenced field ids, comma separated',
  `orig_fieldid` varchar(200) DEFAULT NULL COMMENT 'Referenced raw field ids',
  `author` int(11) NOT NULL COMMENT 'Creator',
  `created` datetime NOT NULL COMMENT 'Creation time',
  PRIMARY KEY (`id`),
  KEY `idx_field_type` (`field_typeid`),
  KEY `idx_field_en` (`field_en`)
) ENGINE=InnoDB AUTO_INCREMENT=668 DEFAULT CHARSET=utf8mb4 COMMENT='Field';

CREATE TABLE `t_field_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Field type id',
  `field_type` varchar(100) NOT NULL COMMENT 'Field type name',
  `parent_id` int(11) NOT NULL DEFAULT '0' COMMENT 'Parent node id',
  `is_common` int(4) NOT NULL DEFAULT '0' COMMENT 'Whether organization-common type',
  PRIMARY KEY (`id`),
  KEY `idx_ft_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COMMENT='Field type';

CREATE TABLE `t_knowledge_tree` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(500) DEFAULT NULL,
  `parent_id` int(11) DEFAULT NULL COMMENT 'Parent node id',
  `user_id` int(11) DEFAULT NULL COMMENT 'Creator id',
  `organ_id` int(11) DEFAULT NULL COMMENT 'Organization id',
  `engine_id` int(11) DEFAULT NULL COMMENT 'Engine id',
  `status` int(2) DEFAULT '1' COMMENT '0 disabled 1 enabled -1 deleted',
  `type` int(2) DEFAULT NULL COMMENT '0 system 1 organization 2 engine',
  `tree_type` int(2) DEFAULT NULL COMMENT '0 rule tree 1 scorecard tree 2 recycle bin tree',
  `created` datetime DEFAULT NULL COMMENT 'Creation time',
  `updated` datetime DEFAULT NULL COMMENT 'Update time',
  PRIMARY KEY (`id`),
  KEY `idx_kt_parent` (`parent_id`),
  KEY `idx_kt_scope` (`engine_id`,`organ_id`,`tree_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=178 DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge base directory';

CREATE TABLE `t_list_db` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'List DB id',
  `list_type` varchar(10) NOT NULL COMMENT 'b blacklist w whitelist',
  `list_name` varchar(100) NOT NULL COMMENT 'List DB name',
  `data_source` int(4) DEFAULT NULL COMMENT '1 external 2 internal 0 unset',
  `list_attr` varchar(100) DEFAULT NULL COMMENT 'Type attribute',
  `list_desc` varchar(1000) DEFAULT NULL,
  `table_column` varchar(200) NOT NULL COMMENT 'Table columns, field ids comma separated',
  `match_type` int(4) DEFAULT NULL COMMENT '1 exact 0 fuzzy',
  `query_type` int(4) DEFAULT NULL COMMENT '1 and 0 or',
  `query_field` varchar(200) DEFAULT NULL COMMENT 'Query keys, field ids comma separated',
  `organ_id` int(11) DEFAULT NULL COMMENT 'Owning organization id',
  `status` int(4) NOT NULL DEFAULT '0' COMMENT '1 enabled 0 disabled -1 deleted',
  `user_id` int(11) NOT NULL COMMENT 'Creator',
  `created` datetime NOT NULL COMMENT 'Creation time',
  PRIMARY KEY (`id`),
  KEY `idx_ld_scope` (`organ_id`,`list_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='Black/whitelist DB configuration';

CREATE TABLE `t_node_knowledge_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `node_id` int(11) DEFAULT NULL COMMENT 'Node id',
  `knowledge_id` int(11) DEFAULT NULL COMMENT 'Rule/scorecard/decision option id',
  `knowledge_type` int(2) DEFAULT NULL COMMENT 'Knowledge type, see KnowledgeType',
  PRIMARY KEY (`id`),
  KEY `idx_nkr_node` (`node_id`,`knowledge_type`),
  KEY `idx_nkr_knowledge` (`knowledge_id`,`knowledge_type`)
) ENGINE=InnoDB AUTO_INCREMENT=23656 DEFAULT CHARSET=utf8mb4 COMMENT='Node to knowledge-base relation';

CREATE TABLE `t_rule` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(500) DEFAULT NULL,
  `code` varchar(500) DEFAULT NULL,
  `description` text COMMENT 'Rule description',
  `priority` int(4) DEFAULT NULL COMMENT 'Priority, smaller runs first',
  `parent_id` int(11) DEFAULT NULL COMMENT 'Parent node id (directory)',
  `created_by` bigint(20) DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL COMMENT 'Modifier',
  `organ_id` int(11) DEFAULT NULL COMMENT 'Organization id',
  `engine_id` int(11) DEFAULT NULL COMMENT 'Engine id',
  `status` int(2) DEFAULT '1' COMMENT '0 disabled 1 enabled -1 deleted',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `type` int(2) DEFAULT NULL COMMENT '0 system 1 organization 2 engine',
  `is_non` int(2) DEFAULT '0' COMMENT 'Negate whole condition set: 0 no 1 yes',
  `content` longtext COMMENT 'Rule content',
  `created_time` datetime DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  `rule_type` smallint(2) DEFAULT '0' COMMENT '0 hard reject 1 score adjust',
  `rule_audit` smallint(2) DEFAULT NULL COMMENT 'Audit status',
  `score` int(11) DEFAULT NULL COMMENT 'Score of a score-adjust rule',
  `last_logical` varchar(50) DEFAULT NULL COMMENT 'Logical connector between conditions AND/OR',
  PRIMARY KEY (`id`),
  KEY `idx_rule_scope` (`engine_id`,`organ_id`,`status`),
  KEY `idx_rule_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=761 DEFAULT CHARSET=utf8mb4 COMMENT='Rule';

CREATE TABLE `t_scorecard` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(500) DEFAULT NULL,
  `code` varchar(200) DEFAULT NULL COMMENT 'Code',
  `description` text COMMENT 'Description',
  `version` varchar(500) DEFAULT NULL COMMENT 'Version',
  `parent_id` int(11) DEFAULT NULL COMMENT 'Parent node id',
  `created_by` bigint(20) DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL COMMENT 'Modifier',
  `organ_id` int(11) DEFAULT NULL COMMENT 'Organization id',
  `engine_id` int(11) DEFAULT NULL COMMENT 'Engine id',
  `type` int(2) DEFAULT NULL COMMENT '0 system 1 organization 2 engine',
  `status` int(2) DEFAULT '1' COMMENT '0 disabled 1 enabled -1 deleted',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `score` longtext COMMENT 'Score-bin mapping JSON',
  `pd` longtext,
  `odds` longtext,
  `created_time` datetime DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sc_scope` (`engine_id`,`organ_id`,`status`),
  KEY `idx_sc_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COMMENT='Scorecard';
