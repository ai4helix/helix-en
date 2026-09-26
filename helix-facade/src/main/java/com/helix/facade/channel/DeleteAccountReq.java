package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** DeleteAccountReq */
@Data
public class DeleteAccountReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accountId;
}
