package com.helix.facade.channel;

import lombok.Data;
import java.io.Serializable;

/** CreatePersonalSealReq */
@Data
public class CreatePersonalSealReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String type;
    private String color;
    private String stampRule;
}
