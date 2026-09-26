-- =====================================================================
-- Bank loan admission decision engine -- demo seed data
-- Scenario: personal consumer credit (bank micro-loan),
--           amount within 50k, terms of 12/24/36 months
-- Engine: BANK_LOAN_ADMIT / Bank Loan Admission Engine
--
-- Execution semantics (consistent with the original helix-rules):
--   t_field.field_en                    data-center retrieval key, also the variable name in expressions
--   t_rule.rule_type   0 hard reject 1 score adjust
--   t_rule.rule_audit  5 pass 2 reject 3 manual review 4 simplified flow
--   t_rule.is_non      1 means negate the whole condition set
--   t_rule_field.logical  connector to the next condition (&& / ||), -1 for the last one
--   t_rule_field.field_id format "{fieldId}|{fieldEn}"
--   t_rule.content     compiled into a rule body executable by Drools/expression engine
-- =====================================================================

USE `helix_en_engine`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Clear data of this scenario (keep other scenarios)
DELETE FROM `t_node_knowledge_rel` WHERE `node_id` IN (SELECT `node_id` FROM `t_engine_node` WHERE `version_id` IN (SELECT `version_id` FROM `t_engine_version` WHERE `engine_id` = 1));
DELETE FROM `t_engine_node` WHERE `version_id` IN (SELECT `version_id` FROM `t_engine_version` WHERE `engine_id` = 1);
DELETE FROM `t_engine_version` WHERE `engine_id` = 1;
DELETE FROM `t_engine` WHERE `id` = 1;
DELETE FROM `t_field_user_rel` WHERE `field_id` IN (SELECT `id` FROM `t_field` WHERE `author` = 1);
DELETE FROM `t_field` WHERE `author` = 1;
DELETE FROM `t_rule_field` WHERE `rule_id` IN (SELECT `id` FROM `t_rule` WHERE `engine_id` = 1 OR `organ_id` = 1);
DELETE FROM `t_rule` WHERE `engine_id` = 1 OR `organ_id` = 1;
DELETE FROM `t_scorecard` WHERE `engine_id` = 1 OR `organ_id` = 1;
DELETE FROM `t_list_db` WHERE `organ_id` = 1;

-- ---------------------------------------------------------------------
-- 1. Field types (keep original categories to avoid frontend tree mismatch)
-- ---------------------------------------------------------------------
INSERT INTO `t_field_type` (`id`, `field_type`, `parent_id`, `is_common`) VALUES
  (1, 'Basic Info', 0, 1),
  (2, 'Credit Info', 0, 1),
  (3, 'Income & Debt', 0, 1),
  (4, 'Behavior Score', 0, 1)
ON DUPLICATE KEY UPDATE `field_type` = VALUES(`field_type`);

-- ---------------------------------------------------------------------
-- 2. Fields
--    value_type: 1 numeric 2 string 3 enum 4 decimal
--    is_derivative: 1 derived field (computed from raw fields, not from data center)
-- ---------------------------------------------------------------------
INSERT INTO `t_field`
  (`id`, `field_en`, `field_cn`, `field_typeid`, `value_type`, `value_scope`, `is_derivative`, `is_output`, `is_common`, `formula`, `formula_show`, `used_fieldid`, `orig_fieldid`, `author`, `created`)
VALUES
  -- Basic Info
  (101, 'applicant_age',            'Applicant Age',          1, 1, '18-65',      0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (102, 'applicant_education',      'Highest Education',      1, 3, '0-5',        0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (103, 'applicant_marital',        'Marital Status',         1, 3, '1Married2Single3Divorced', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (104, 'resident_city_level',      'Resident City Tier',     1, 3, '1Tier-1 City2Tier-2 City3Tier-3 City4Other', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),

  -- Credit Info
  (111, 'helix_score',             'Credit Bureau Score',    2, 1, '300-850',    0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (112, 'helix_query_3m',          'Credit Queries in Last 3 Months', 2, 1, '0-30',    0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (113, 'helix_query_6m',          'Credit Queries in Last 6 Months', 2, 1, '0-50',    0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (114, 'helix_overdue_cnt',       'Current Overdue Count',  2, 1, '0-20',       0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (115, 'helix_overdue_max_days',  'Max Overdue Days in Last 2 Years', 2, 1, '0-999', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (116, 'helix_card_usage_rate',   'Credit Card Utilization', 2, 4, '0-1',       0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (117, 'helix_history_months',    'Credit History Months',  2, 1, '0-600',      0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (118, 'loan_org_cnt',             'Active Loan Institutions', 2, 1, '0-30',    0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),

  -- Income & Debt
  (121, 'monthly_income',           'Monthly Income (CNY)',   3, 4, '0-200000',   0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (122, 'monthly_debt',             'Monthly Debt Payment (CNY)', 3, 4, '0-100000', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (123, 'debt_income_ratio',        'Debt-to-Income Ratio',   3, 4, '0-5',        1, 0, 1,
       'monthly_debt / monthly_income', 'Monthly Debt Payment ÷ Monthly Income', '122,121', '122,121', 1, NOW()),
  (124, 'apply_amount',             'Applied Amount (CNY)',   3, 4, '0-500000',   0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (125, 'apply_term',               'Applied Term (Months)',  3, 1, '3-60',       0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (126, 'social_security_months',   'Consecutive Social Security Months', 3, 1, '0-600', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),

  -- Behavior Score
  (131, 'multi_loan_cnt_30d',       'Multi-Loan Applications in 30d', 4, 1, '0-50', 0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (132, 'night_apply_flag',         'Night Application Flag', 4, 1, '0/1',        0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (133, 'device_risk_score',        'Device Risk Score',      4, 1, '0-100',      0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (134, 'blacklist_hit_flag',       'Blacklist Hit Flag',     4, 1, '0/1',        0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW()),
  (135, 'fraud_score',              'Anti-Fraud Model Score', 4, 1, '0-100',      0, 0, 1, NULL, NULL, NULL, NULL, 1, NOW());

INSERT INTO `t_field_user_rel` (`field_id`, `organ_id`, `engine_id`, `user_id`, `status`, `created`, `updated`)
SELECT `id`, 1, NULL, 1, 1, NOW(), NOW() FROM `t_field` WHERE `author` = 1;

DELETE FROM `t_formula_field_rel` WHERE `field_id` = 123;
INSERT INTO `t_formula_field_rel` (`field_id`, `formula_fieldid`) VALUES (123, 121), (123, 122);

-- ---------------------------------------------------------------------
-- 3. Engine
-- ---------------------------------------------------------------------
INSERT INTO `t_engine` (`id`, `code`, `name`, `description`, `status`, `create_datetime`, `update_datetime`, `creator`, `organ_id`, `user_id`)
VALUES (1, 'BANK_LOAN_ADMIT', 'Bank Loan Admission Engine', 'Personal consumer credit admission decision: anti-fraud → hard rejection → credit scorecard → debt check → final decision', 1, NOW(), NOW(), 1, 1, 1);

-- ---------------------------------------------------------------------
-- 4. Rules
-- ---------------------------------------------------------------------
INSERT INTO `t_rule`
  (`id`, `name`, `code`, `description`, `priority`, `parent_id`, `author`, `user_id`, `organ_id`, `engine_id`, `status`, `type`, `is_non`, `content`, `created`, `updated`, `rule_type`, `rule_audit`, `score`, `last_logical`)
VALUES
  (1001, 'Anti-Fraud Hit Reject', 'R_BL_ANTI_FRAUD', 'Anti-fraud model score too high or blacklist hit, reject directly', 10, NULL, 1, 1, 1, 1, 1, 1, 0,
   'blacklist_hit_flag == 1 || fraud_score >= 85', NOW(), NOW(), 0, 2, NULL, '-1'),

  (1002, 'Age Admission Reject', 'R_BL_AGE', 'Age outside the 22-55 range', 20, NULL, 1, 1, 1, 1, 1, 1, 0,
   'applicant_age < 22 || applicant_age > 55', NOW(), NOW(), 0, 2, NULL, '-1'),

  (1003, 'Severe Credit Defect Reject', 'R_BL_HELIX_BAD', 'Currently overdue, or max overdue days in last 2 years exceeds 60', 30, NULL, 1, 1, 1, 1, 1, 1, 0,
   'helix_overdue_cnt >= 1 || helix_overdue_max_days > 60', NOW(), NOW(), 0, 2, NULL, '-1'),

  (1004, 'Multi-Loan Reject', 'R_BL_MULTI_LOAN', 'More than 8 active loan institutions and more than 10 multi-loan applications in last 30 days', 40, NULL, 1, 1, 1, 1, 1, 1, 0,
   'loan_org_cnt > 8 && multi_loan_cnt_30d > 10', NOW(), NOW(), 0, 2, NULL, '-1'),

  (1005, 'Excessive Credit Queries to Manual Review', 'R_MR_QUERY_FREQ', 'More than 8 queries in last 3 months or 15 in last 6 months, route to manual review', 50, NULL, 1, 1, 1, 1, 1, 1, 0,
   'helix_query_3m > 8 || helix_query_6m > 15', NOW(), NOW(), 1, 3, 0, '-1'),

  (1006, 'Insufficient Income to Manual Review', 'R_MR_INCOME_LOW', 'Monthly income below 5000 and social security paid less than 6 months, route to manual review', 60, NULL, 1, 1, 1, 1, 1, 1, 0,
   'monthly_income < 5000 && social_security_months < 6', NOW(), NOW(), 1, 3, 0, '-1'),

  (1007, 'High Risk Signal Deduction', 'R_SC_HIGH_RISK', 'Night application, high device risk, or excessive credit card utilization, deduct points', 70, NULL, 1, 1, 1, 1, 1, 1, 0,
   'night_apply_flag == 1 || device_risk_score >= 70 || helix_card_usage_rate > 0.8', NOW(), NOW(), 1, 4, -15, '-1'),

  (1008, 'Premium Customer Bonus', 'R_SC_PREMIUM', 'Bachelor degree or above, credit history over 3 years, social security over 24 consecutive months', 80, NULL, 1, 1, 1, 1, 1, 1, 0,
   'applicant_education >= 4 && helix_history_months >= 36 && social_security_months >= 24', NOW(), NOW(), 1, 5, 15, '-1'),

  (1009, 'High Debt Ratio Deduction', 'R_SC_DEBT_RATIO', 'Debt-to-income ratio exceeds 0.7, deduct points', 90, NULL, 1, 1, 1, 1, 1, 1, 0,
   'debt_income_ratio > 0.7', NOW(), NOW(), 1, 4, -20, '-1');

INSERT INTO `t_rule_field` (`rule_id`, `logical`, `operator`, `field_value`, `field_id`) VALUES
  (1001, '||', '==', '1',  '134|blacklist_hit_flag'),
  (1001, '||', '>=', '85', '135|fraud_score'),

  (1002, '||', '<',  '22', '101|applicant_age'),
  (1002, '||', '>',  '55', '101|applicant_age'),

  (1003, '||', '>=', '1',  '114|helix_overdue_cnt'),
  (1003, '||', '>',  '60', '115|helix_overdue_max_days'),

  (1004, '&&', '>',  '8',  '118|loan_org_cnt'),
  (1004, '&&', '>',  '10', '131|multi_loan_cnt_30d'),

  (1005, '||', '>',  '8',  '112|helix_query_3m'),
  (1005, '||', '>',  '15', '113|helix_query_6m'),

  (1006, '&&', '<',  '5000', '121|monthly_income'),
  (1006, '&&', '<',  '6',    '126|social_security_months'),

  (1007, '||', '==', '1',   '132|night_apply_flag'),
  (1007, '||', '>=', '70',  '133|device_risk_score'),
  (1007, '||', '>',  '0.8', '116|helix_card_usage_rate'),

  (1008, '&&', '>=', '4',  '102|applicant_education'),
  (1008, '&&', '>=', '36', '117|helix_history_months'),
  (1008, '&&', '>=', '24', '126|social_security_months'),

  (1009, '-1', '>', '0.7', '123|debt_income_ratio');

-- ---------------------------------------------------------------------
-- 5. Scorecard
-- ---------------------------------------------------------------------
INSERT INTO `t_scorecard`
  (`id`, `name`, `code`, `description`, `version`, `parent_id`, `author`, `user_id`, `organ_id`, `engine_id`, `type`, `status`, `score`, `pd`, `odds`, `created`, `updated`)
VALUES
  (2001, 'Credit Composite Scorecard', 'SC_HELIX_COMPOSITE',
   'Credit bureau five-dimension scoring: credit score, query frequency, overdue behavior, debt level, credit history. Weighted total 100',
   'v1.0', NULL, 1, 1, 1, 1, 2, 1,
   '[{"field":"helix_score","weight":30,"type":"score","bins":[{"min":750,"max":900,"score":30},{"min":700,"max":750,"score":25},{"min":650,"max":700,"score":18},{"min":600,"max":650,"score":10},{"min":0,"max":600,"score":0}]},{"field":"helix_query_3m","weight":15,"type":"inverse","bins":[{"min":0,"max":2,"score":15},{"min":2,"max":4,"score":11},{"min":4,"max":8,"score":6},{"min":8,"max":99,"score":0}]},{"field":"helix_overdue_max_days","weight":25,"type":"inverse","bins":[{"min":0,"max":1,"score":25},{"min":1,"max":30,"score":15},{"min":30,"max":60,"score":6},{"min":60,"max":9999,"score":0}]},{"field":"debt_income_ratio","weight":20,"type":"inverse","bins":[{"min":0,"max":0.3,"score":20},{"min":0.3,"max":0.5,"score":14},{"min":0.5,"max":0.7,"score":8},{"min":0.7,"max":99,"score":0}]},{"field":"helix_history_months","weight":10,"type":"score","bins":[{"min":60,"max":9999,"score":10},{"min":36,"max":60,"score":8},{"min":12,"max":36,"score":5},{"min":0,"max":12,"score":0}]}]',
   '{"650":"0.08","600":"0.15","550":"0.28","500":"0.45"}',
   '{"650":"11.5","600":"5.7","550":"2.6","500":"1.2"}',
   NOW(), NOW());

INSERT INTO `t_scorecard_field` (`scorecard_id`, `field_id`) VALUES
  (2001, 111), (2001, 112), (2001, 115), (2001, 123), (2001, 117);

-- ---------------------------------------------------------------------
-- 6. List DBs
-- ---------------------------------------------------------------------
INSERT INTO `t_list_db`
  (`id`, `list_type`, `list_name`, `data_source`, `list_attr`, `list_desc`, `table_column`, `match_type`, `query_type`, `query_field`, `organ_id`, `status`, `user_id`, `created`)
VALUES
  (3001, 'b', 'Bank Loan Blacklist', 2, 'Internal Blacklist', 'Overdue over 90 days, confirmed fraud, court dishonest debtors', '134|blacklist_hit_flag', 1, 1, '134|blacklist_hit_flag', 1, 1, 1, NOW()),
  (3002, 'w', 'Bank Loan Whitelist', 2, 'Internal Whitelist', 'Existing premium customers, payroll customers of the bank', '134|blacklist_hit_flag', 1, 1, '134|blacklist_hit_flag', 1, 1, 1, NOW());

-- ---------------------------------------------------------------------
-- 7. Decision flow (version + nodes)
-- ---------------------------------------------------------------------
INSERT INTO `t_engine_version`
  (`version_id`, `engine_id`, `version`, `sub_version`, `boot_state`, `status`, `layout`, `user_id`, `create_time`)
VALUES (9001, 1, 1, 1, 1, 1, 0, 1, NOW());

INSERT INTO `t_engine_node`
  (`node_id`, `version_id`, `node_name`, `node_code`, `node_order`, `node_type`, `node_json`, `node_x`, `node_y`, `next_nodes`)
VALUES
  (9101, 9001, 'Start',               'start',             0, 1,  NULL, 60.00,  420.00, 'anti_fraud'),
  (9102, 9001, 'Anti-Fraud Check',    'anti_fraud',        1, 5,  NULL, 280.00, 420.00, 'hard_reject,helix_scorecard'),
  (9103, 9001, 'Hard Reject Rules',   'hard_reject',       2, 2,  NULL, 520.00, 180.00, 'manual_review'),
  (9104, 9001, 'Credit Scorecard',    'helix_scorecard',   3, 4,  NULL, 520.00, 660.00, 'adjust_score'),
  (9105, 9001, 'Manual Review Rules', 'manual_review',     4, 2,  NULL, 800.00, 180.00, 'sandbox'),
  (9106, 9001, 'Score Adjust Rules',  'adjust_score',      5, 2,  NULL, 800.00, 660.00, 'sandbox'),
  (9107, 9001, 'Sandbox 10%',         'sandbox',           6, 7,
   '{"ratio":10}', 1080.00, 420.00, 'final_decision'),
  (9108, 9001, 'Final Decision',      'final_decision',    7, 9,
   '{"result":"3","rules":[{"minScore":80,"result":"1"},{"minScore":50,"result":"3"}]}',
   1360.00, 420.00, '');

INSERT INTO `t_node_knowledge_rel` (`node_id`, `knowledge_id`, `knowledge_type`) VALUES
  (9102, 3001, 1),
  (9103, 1001, 1), (9103, 1002, 1), (9103, 1003, 1), (9103, 1004, 1),
  (9104, 2001, 2),
  (9105, 1005, 1), (9105, 1006, 1),
  (9106, 1007, 1), (9106, 1008, 1), (9106, 1009, 1);

-- ---------------------------------------------------------------------
-- 8. Done
-- ---------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 1;
