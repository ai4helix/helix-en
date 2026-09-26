package com.helix.console.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_tree")
public class KnowledgeTree implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private Integer parentId;

    private Integer engineId;

    private Integer status;

    private Integer type;

    private Integer treeType;

    private Integer organId;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
