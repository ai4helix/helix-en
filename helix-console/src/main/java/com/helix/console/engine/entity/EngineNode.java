package com.helix.console.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("t_engine_node")
public class EngineNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "node_id", type = IdType.AUTO)
    private Integer nodeId;

    private Integer versionId;

    private String nodeName;

    private String nodeCode;

    private Integer nodeOrder;

    private Integer nodeType;

    private String nodeJson;

    private BigDecimal nodeX;

    private BigDecimal nodeY;

    private String nodeScript;

    private String nextNodes;

    private String params;

    private Integer parentId;
}
