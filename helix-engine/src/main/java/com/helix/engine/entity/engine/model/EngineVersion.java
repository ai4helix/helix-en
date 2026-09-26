package com.helix.engine.entity.engine.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

import java.util.List;

@Data
@TableName("t_engine_version")
public class EngineVersion implements Comparable<EngineVersion> {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer engineId;

    private Integer version;

    private Integer subVersion;

    private Short bootState;

    private Short status;

    private Short layout;

    private LocalDateTime createdTime;

    

    

    @TableField(exist = false)
    private List<EngineNode> engineNodeList;

    @TableField(exist = false)
    private String engineName;

    @TableField(exist = false)
    private String engineDesc;

    @Override
    public int compareTo(EngineVersion o) {
        if (version != o.getVersion()) {
            return version - o.getVersion();
        } else if (!(subVersion == o.getSubVersion())) {
            return subVersion - o.getSubVersion();
        } else {
            return version - o.getVersion();
        }
    }
}