-- MySQL dump 10.13  Distrib 5.7.41, for Linux (x86_64)
--
-- Host: localhost    Database: helix_en_console
-- ------------------------------------------------------
-- Server version	5.7.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `t_feedback`
--

DROP TABLE IF EXISTS `t_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `organ_id` int(11) NOT NULL DEFAULT '0' COMMENT 'Owning organization id(0=platform-shared)',
  `user_id` bigint(20) NOT NULL COMMENT 'Submitting user id',
  `username` varchar(64) NOT NULL COMMENT 'Submitting user account',
  `category` tinyint(4) NOT NULL DEFAULT '1' COMMENT 'Type: 1 issue 2 suggestion',
  `content` varchar(2000) NOT NULL COMMENT 'Feedback body (1~2000 )',
  `images` varchar(2000) DEFAULT NULL COMMENT 'Screenshot filename JSON  array (≤4  images, stored in  uploads/feedback/)',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Handle status: 0 pending 1 handled',
  `reply` varchar(1000) DEFAULT NULL COMMENT 'Platform handling opinion',
  `handled_by` varchar(64) DEFAULT NULL COMMENT 'Handler account (platform admin)',
  `handled_time` datetime DEFAULT NULL COMMENT 'Handled time',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Submission time',
  PRIMARY KEY (`id`),
  KEY `idx_organ` (`organ_id`),
  KEY `idx_status_created` (`status`,`created_time`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='User feedback (issues/ suggestions)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_feedback`
--

LOCK TABLES `t_feedback` WRITE;
/*!40000 ALTER TABLE `t_feedback` DISABLE KEYS */;
INSERT INTO `t_feedback` VALUES (1,2,14,'13700000001',1,'Direct backend verification: tenant admin submits test',NULL,0,NULL,NULL,NULL,'2026-09-25 12:54:36'),(2,2,14,'13700000001',2,'In-browser  fetch direct test (valid token): suggestion feedback',NULL,0,NULL,NULL,NULL,'2026-09-25 12:57:47'),(3,2,14,'13700000001',1,'Real-path submit after page refresh: verify navbar button entry',NULL,0,NULL,NULL,NULL,'2026-09-25 12:59:35');
/*!40000 ALTER TABLE `t_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_organization`
--

DROP TABLE IF EXISTS `t_organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_organization` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT 'Organization name',
  `code` varchar(100) NOT NULL COMMENT 'Organization code',
  `email` varchar(100) DEFAULT NULL COMMENT 'Email',
  `telephone` varchar(100) DEFAULT NULL COMMENT 'Telephone',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 disabled 1 enabled',
  `author` varchar(100) DEFAULT NULL COMMENT 'Creator',
  `birth` datetime DEFAULT NULL COMMENT 'Creation time',
  `token` varchar(100) DEFAULT NULL COMMENT 'Unique token',
  `demo_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'demoDemo data init status 0=not initialized 1=initialized(v5.6.41)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COMMENT='organization';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_organization`
--

LOCK TABLES `t_organization` WRITE;
/*!40000 ALTER TABLE `t_organization` DISABLE KEYS */;
INSERT INTO `t_organization` VALUES (1,'Platform Operator','0001','123.com ','1234567489',1,'Super Administrator','2017-06-29 15:32:42','6a6ea35e-aabe-4e64-bd98-dae304b10a21',1),(2,'Demo Tenant Tech','0002',NULL,'13800000002',1,'admin','2026-09-21 09:56:29','a4441b77-b706-11f1-b2ad-e631ef0a9637',1),(50,'E2EDemo Tenant','T1790239377078181',NULL,'13900011234',1,'register','2026-09-24 16:42:57','fc1b3672-37fa-40ba-8444-00c48a191c4d',1),(51,'Demo Tenant Ltd.','T1790259564109341',NULL,'13020202020',1,'register','2026-09-24 22:19:24','05f024af-23e2-418e-adae-3795223ead58',1);
/*!40000 ALTER TABLE `t_organization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_resource`
--

DROP TABLE IF EXISTS `t_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_resource` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT 'Assigner',
  `name` varchar(100) NOT NULL COMMENT 'Resource name',
  `code` varchar(100) NOT NULL COMMENT 'Resource code',
  `url` varchar(100) NOT NULL COMMENT 'Resource path',
  `parent_id` bigint(20) DEFAULT NULL COMMENT 'Parent node',
  `des` varchar(100) DEFAULT NULL COMMENT 'Resource description',
  `birth` datetime DEFAULT NULL COMMENT 'Creation time',
  `icon` varchar(100) DEFAULT NULL COMMENT 'Icon',
  `status` tinyint(1) DEFAULT '1' COMMENT 'Status',
  PRIMARY KEY (`id`),
  KEY `idx_res_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=86 DEFAULT CHARSET=utf8mb4 COMMENT='Resource (menu/ API)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_resource`
--

LOCK TABLES `t_resource` WRITE;
/*!40000 ALTER TABLE `t_resource` DISABLE KEYS */;
INSERT INTO `t_resource` VALUES (1,NULL,'Decision Flow','menu:flow','/flow',0,'Decision Flow Canvas','2026-09-21 09:56:29','Share',1),(2,NULL,'Knowledge Base','menu:knowledge','/knowledge',0,'Rules and scorecards','2026-09-21 09:56:29','Collection',1),(3,NULL,'Data Management','menu:datamanage','/datamanage',0,'Fields and list DBs','2026-09-21 09:56:29','Files',1),(4,NULL,'Execution Result','menu:result','/result',0,'Result sets and batch testing','2026-09-21 09:56:29','Histogram',1),(5,NULL,'System Management','menu:system','/system',0,'Users, roles, organizations','2026-09-21 09:56:29','Setting',1),(7,NULL,'Decision Table','menu:dtable','/dtable',0,'Decision table matrix editing (DMN  mode)','2026-09-22 21:06:27','Grid',1),(8,NULL,'Batch Run','menu:batch','/batch',0,'Fields/user indicator data import, run batches and download results','2026-09-23 10:00:00','DataAnalysis',1),(9,NULL,'Platform Operations','menu:platform','/platform',0,'tenant list, role list and per-tenant engine-run statistics','2026-09-23 12:00:00','Odometer',1),(11,NULL,'View Rules','rule:view','',2,'View rules','2026-09-21 09:56:29','',1),(12,NULL,'Edit Rules','rule:edit','',2,'Create or modify rules','2026-09-21 09:56:29','',1),(13,NULL,'ruleDelete','rule:delete','',2,'Delete Rules','2026-09-21 09:56:29','',1),(14,NULL,'Edit Scorecards','scorecard:edit','',2,'Create or modify scorecards','2026-09-21 09:56:29','',1),(21,NULL,'Edit Decision Flow','flow:edit','',1,'Edit decision flows','2026-09-21 09:56:29','',1),(22,NULL,'Publish Decision Flow','flow:publish','',1,'Publish versions','2026-09-21 09:56:29','',1),(31,NULL,'Edit List DB','listdb:edit','',3,'Create or modify list DBs','2026-09-21 09:56:29','',1),(32,NULL,'View Fields','field:view','',3,'View fields','2026-09-21 09:56:29','',1),(41,NULL,'View Results','result:view','',4,'View execution results','2026-09-21 09:56:29','',1),(42,NULL,'Batch Test','result:batch','',4,'Run batch tests','2026-09-21 09:56:29','',1),(51,NULL,'User Management','user:manage','',5,'User CRUD','2026-09-21 09:56:29','',1),(52,NULL,'Role Management','role:manage','',5,'Roles and authorization','2026-09-21 09:56:29','',1),(53,NULL,'Organization Management','org:manage','',5,'Organization CRUD','2026-09-21 09:56:29','',1),(71,NULL,'Data Lineage','menu:lineage','/lineage',0,'Decision-flow-level lineage graph: field →Knowledge object→node→version','2026-09-24 00:03:56','Connection',1),(81,NULL,'Run Center','RUN_CENTER','/run',0,'Indicator→rule→Decision Flow→unified trial-run entry for run','2026-09-24 07:07:36','VideoPlay',1),(82,NULL,'Workbench','WORKBENCH','/workbench',0,'Indicator→rule→Decision Flow→run Unified four-layer workbench','2026-09-24 09:25:51','HomeFilled',1),(83,NULL,'User Guide','MENU_GUIDE','/guide',0,'Quick start: build a decision in four steps, canvas operations, trial runs and batch run guide','2026-09-24 10:04:57','QuestionFilled',1),(84,NULL,'Fields','MENU_FIELD','/fields',0,'Indicator field management: single-item maintenance and bulk import','2026-09-24 11:19:29','Coin',1),(85,NULL,'Feedback Management','MENU_FEEDBACK','/feedback',0,'User issues and suggestions (handled on platform side)','2026-09-25 12:31:15','ChatDotRound',1);
/*!40000 ALTER TABLE `t_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_role`
--

DROP TABLE IF EXISTS `t_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `organ_id` bigint(20) DEFAULT NULL COMMENT 'Organization id',
  `role_name` varchar(100) NOT NULL COMMENT 'Role name',
  `role_code` varchar(100) DEFAULT NULL COMMENT 'Role code',
  `role_desc` varchar(100) DEFAULT NULL COMMENT 'Role description',
  `author` varchar(100) DEFAULT NULL COMMENT 'Creator',
  `birth` datetime DEFAULT NULL COMMENT 'Creation time',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 disabled 1 enabled',
  PRIMARY KEY (`id`),
  KEY `idx_role_organ` (`organ_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COMMENT='role';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_role`
--

LOCK TABLES `t_role` WRITE;
/*!40000 ALTER TABLE `t_role` DISABLE KEYS */;
INSERT INTO `t_role` VALUES (1,1,'Super Administrator','ROLE_ADMIN','Has all permissions','system','2026-09-21 09:56:29',1),(2,0,'Operator','ROLE_TENANT_OPERATOR','Tenant Operator: business configuration (fields/rule/Decision Flow/batch) and run verification, excl','system','2026-09-21 09:56:29',1),(8,0,'Admin','ROLE_TENANT_ADMIN','Tenant Admin (bound at registration): all functions of this organization, incl. user management','system','2026-09-23 10:00:00',1);
/*!40000 ALTER TABLE `t_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_role_resource_rel`
--

DROP TABLE IF EXISTS `t_role_resource_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_role_resource_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `role_id` bigint(20) DEFAULT NULL COMMENT 'role id',
  `resource_id` bigint(20) DEFAULT NULL COMMENT 'Resource id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rr` (`role_id`,`resource_id`)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COMMENT='Role-resource relation';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_role_resource_rel`
--

LOCK TABLES `t_role_resource_rel` WRITE;
/*!40000 ALTER TABLE `t_role_resource_rel` DISABLE KEYS */;
INSERT INTO `t_role_resource_rel` VALUES (1,1,1),(2,1,2),(3,1,3),(4,1,4),(5,1,5),(56,1,7),(55,1,8),(8,1,11),(9,1,12),(10,1,13),(11,1,14),(6,1,21),(7,1,22),(12,1,31),(13,1,32),(14,1,41),(15,1,42),(16,1,51),(17,1,52),(18,1,53),(80,1,71),(83,1,81),(87,1,82),(91,1,83),(140,1,85),(119,2,1),(120,2,2),(121,2,3),(122,2,4),(123,2,7),(124,2,8),(125,2,11),(126,2,12),(127,2,14),(128,2,21),(129,2,22),(130,2,31),(131,2,32),(132,2,41),(133,2,42),(134,2,71),(135,2,81),(136,2,82),(137,2,83),(138,2,84),(95,8,1),(96,8,2),(97,8,3),(98,8,4),(99,8,5),(100,8,7),(101,8,8),(102,8,11),(103,8,12),(104,8,13),(105,8,14),(106,8,21),(107,8,22),(108,8,31),(109,8,32),(110,8,41),(111,8,42),(112,8,51),(113,8,52),(114,8,53),(115,8,71),(116,8,81),(117,8,82),(118,8,83),(139,8,84);
/*!40000 ALTER TABLE `t_role_resource_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_user`
--

DROP TABLE IF EXISTS `t_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `organ_id` bigint(20) DEFAULT NULL COMMENT 'Organization id',
  `user_type` tinyint(4) NOT NULL DEFAULT '2' COMMENT 'User type: 1 Platform admin user 2 SaaS org user',
  `employee_id` varchar(100) DEFAULT NULL COMMENT 'Employee ID',
  `account` varchar(100) NOT NULL COMMENT 'Account (mobile or email)',
  `password` varchar(100) NOT NULL COMMENT 'Password (BCrypt)',
  `nick_name` varchar(100) NOT NULL COMMENT 'Nickname',
  `email` varchar(100) DEFAULT NULL COMMENT 'Email',
  `cellphone` varchar(100) DEFAULT NULL COMMENT 'mobile',
  `qq` varchar(100) DEFAULT NULL COMMENT 'QQ',
  `latest_time` datetime DEFAULT NULL COMMENT 'Last login time',
  `latest_ip` varchar(100) DEFAULT NULL COMMENT 'Last login IP',
  `status` tinyint(1) DEFAULT '1' COMMENT '0 disabled 1 enabled -1 soft delete',
  `birth` datetime DEFAULT NULL COMMENT 'Creation time',
  `author` varchar(100) DEFAULT NULL COMMENT 'Creator',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`),
  KEY `idx_user_organ` (`organ_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COMMENT='user';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_user`
--

LOCK TABLES `t_user` WRITE;
/*!40000 ALTER TABLE `t_user` DISABLE KEYS */;
INSERT INTO `t_user` VALUES (1,1,1,'E0001','admin','$2a$10$AZLk/Xx2Eo0ulFQf3tzf.uoEWUVvCsEvVmOVG8z9UrmiEwhqxcove','Super Administrator','admin@credit-x6.local','13800000001',NULL,'2026-09-25 12:59:24','127.0.0.1',1,'2026-09-21 09:56:29','system'),(2,2,2,'E0002','strategist','$2a$10$AZLk/Xx2Eo0ulFQf3tzf.uoEWUVvCsEvVmOVG8z9UrmiEwhqxcove','Strategist Zhang','zhang@credit-x6.local','13800000002',NULL,'2026-09-25 14:31:12','127.0.0.1',1,'2026-09-21 09:56:29','admin'),(3,2,2,'E0003','approver','$2a$10$AZLk/Xx2Eo0ulFQf3tzf.uoEWUVvCsEvVmOVG8z9UrmiEwhqxcove','Approver Li','li@credit-x6.local','13800000003',NULL,'2026-09-24 10:57:38','127.0.0.1',1,'2026-09-21 09:56:29','admin'),(6,2,2,'E9999','testuser','$2a$10$X/ySJ7KX.VmvnfLa.xR3XOrbN0.irKqKKPDNYjM9wq2/Fw/n3JHtG','Test User','t@x6.local','13900000009',NULL,'2026-09-23 12:43:21','0:0:0:0:0:0:0:1',1,'2026-09-21 10:09:01','admin'),(14,2,2,'E0007','13700000001','$2a$10$AZLk/Xx2Eo0ulFQf3tzf.uoEWUVvCsEvVmOVG8z9UrmiEwhqxcove','Demo Tenant Admin','ta@helix-x6.local','13700000001',NULL,'2026-09-26 10:48:18','127.0.0.1',1,'2026-09-23 12:43:57','admin'),(17,2,2,NULL,'13800000002','$2a$10$oq/qa8iVLWZXFJv2edRVHeSzgXSiWq1O5XDQSLmZ3NSZ15nD2rL2e','Account already exists',NULL,NULL,NULL,NULL,NULL,1,'2026-09-23 13:01:40','13700000001'),(18,50,2,NULL,'13900011234','$2a$10$UHyXMpjkHdeROH1PPLuzaeFOCetXwEVaRPY9jU5dBZbT/yt.UwzLS','E2EDemo tenant admin',NULL,'13900011234',NULL,'2026-09-24 16:52:23','0:0:0:0:0:0:0:1',1,'2026-09-24 16:42:57','register'),(19,51,2,NULL,'13020202020','$2a$10$dA8Zh/sjsLIsH5KbCy/4RO6anhtXX1.d0jRYpiqw682Sn2YhM/B.i','Demo Tenant Ltd. admin',NULL,'13020202020',NULL,'2026-09-24 22:24:14','127.0.0.1',1,'2026-09-24 22:19:24','register');
/*!40000 ALTER TABLE `t_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_user_role_rel`
--

DROP TABLE IF EXISTS `t_user_role_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `t_user_role_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT 'User id',
  `role_id` bigint(20) DEFAULT NULL COMMENT 'Role id',
  `organ_id` bigint(20) DEFAULT NULL COMMENT 'Company ID',
  `status` int(10) DEFAULT '1' COMMENT '1 enabled 0 disabled -1 Delete',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ur` (`user_id`,`role_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COMMENT='User-role relation';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_user_role_rel`
--

LOCK TABLES `t_user_role_rel` WRITE;
/*!40000 ALTER TABLE `t_user_role_rel` DISABLE KEYS */;
INSERT INTO `t_user_role_rel` VALUES (1,1,1,1,1),(2,2,2,2,1),(3,3,2,2,1),(6,6,2,2,1),(12,14,8,2,1),(15,17,2,2,1),(16,18,8,50,1),(17,19,8,51,1);
/*!40000 ALTER TABLE `t_user_role_rel` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-26 18:57:35
