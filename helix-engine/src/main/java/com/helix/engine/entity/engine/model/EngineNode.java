package com.helix.engine.entity.engine.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@TableName("t_engine_node")
public class EngineNode implements Serializable {

    @TableId(value = "node_id", type = IdType.AUTO)
    private Integer nodeId;

    private Integer versionId;

    private String nodeName;

    private String nodeCode;

    private Integer nodeOrder;

    private Integer nodeType;

    private BigDecimal nodeX;

    private BigDecimal nodeY;

    private Integer parentId;

    private String nodeJson;

    private String nodeScript;

    private String nextNodes;

    @TableField("params")
    private String nodeParams;

    @TableField(exist = false)
    private Long cardId;

    @TableField(exist = false)
    private List<Integer> ruleList;

    @TableField(exist = false)
    private Integer lastNextNode;
}