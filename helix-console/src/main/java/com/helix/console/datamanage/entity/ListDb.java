package com.helix.console.datamanage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_list_db")
public class ListDb implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String listType;

    private String listName;

    private Integer dataSource;

    private String listAttr;

    private String listDesc;

    private String tableColumn;

    private Integer matchType;

    /** 1 AND 0 OR */
    private Integer queryType;

    private String queryField;

    private Integer organId;

    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
