package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** QueryAccountRsp */
@Data
public class QueryAccountRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String idType;
    private String name;
    private String accountId;
}
