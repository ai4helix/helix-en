package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** BaiHangRsp */
@Data

public class BaiHangRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Object body;
}
