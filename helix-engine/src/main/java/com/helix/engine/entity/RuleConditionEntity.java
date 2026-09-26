package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rule condition AST node. Corresponds to {@code t_rule_condition}.
 *
 * <p>Stores the condition tree as "adjacency list + sibling ordering": {@code parent_id} points to the
 * parent node (NULL for root), {@code sort_no} orders siblings. All rows are fetched at load time and
 * assembled in memory into a {@code Condition} tree; execution never touches this table again.</p>
 */
@Data
@TableName("t_rule_condition")
public class RuleConditionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Owning rule t_rule.id */
    private Integer ruleId;

    /** Parent node id; NULL means root */
    private Long parentId;

    /** 1 leaf / 2 AND group / 3 OR group / 4 NOT */
    private Integer nodeType;

    /** Leaf node: field code */
    private String fieldCode;

    /** Leaf node: operator enum name */
    private String operator;

    /** Leaf node: comparison value; multiple values comma-separated */
    private String value;

    /** Sibling order */
    private Integer sortNo;

    /** Node depth, root = 1 */
    private Integer depth;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    /** Logical delete 0/1 */
    private Integer deleted;
}
