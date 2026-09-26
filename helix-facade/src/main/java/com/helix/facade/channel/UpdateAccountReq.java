package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** UpdateAccountReq */
@Data
public class UpdateAccountReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accountId;
    private String name;
}
