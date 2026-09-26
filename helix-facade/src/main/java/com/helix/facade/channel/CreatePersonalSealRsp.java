package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** CreatePersonalSealRsp */
@Data

public class CreatePersonalSealRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sealData;
}
