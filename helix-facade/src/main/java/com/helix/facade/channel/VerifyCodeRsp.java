package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

/** VerifyCodeRsp */
@Data
@EqualsAndHashCode(callSuper = true)
public class VerifyCodeRsp extends BaseRsp implements Serializable {

    private static final long serialVersionUID = 1L;
}
