package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** CreateAccountReq */
@Data
public class CreateAccountReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
}
