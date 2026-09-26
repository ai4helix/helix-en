package com.helix.console.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long organId;

    private Integer userType;

    private String employeeId;

    private String account;

    private String password;

    private String nickName;

    private String email;

    private String cellphone;

    private String qq;

    private LocalDateTime latestTime;

    private String latestIp;

    private Integer status;

    private LocalDateTime birth;

    private String author;
}
