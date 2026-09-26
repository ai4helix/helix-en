package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** DeleteAccountRsp */
@Data

public class DeleteAccountRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accountId;
}
