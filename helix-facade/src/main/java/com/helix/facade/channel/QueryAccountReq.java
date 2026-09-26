package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** QueryAccountReq */
@Data
public class QueryAccountReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String queryType;
    private String id;
    private String idType;
    private String name;
    private String accountId;
}
