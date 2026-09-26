package com.helix.facade.channel;

import com.helix.facade.engine.HelixOrder;
import com.helix.facade.engine.HelixUser;
import lombok.Data;
import java.io.Serializable;

/** BankHelixReq */
@Data
public class BankHelixReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private HelixUser helixUser;
    private HelixOrder helixOrder;
}
