package com.helix.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Node-to-knowledge relation. Corresponds to {@code t_node_knowledge_rel}.
 */
@Data
@TableName("t_node_knowledge_rel")
public class NodeKnowledgeRel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer nodeId;

    /** Rule / Scorecard / Decision option id */
    private Integer knowledgeId;

    /** 1 rule 2 scorecard 3 decision option 4 complex rule */
    private Integer knowledgeType;
}
