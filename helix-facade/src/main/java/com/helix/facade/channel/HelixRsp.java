package com.helix.facade.channel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;


/** HelixRsp */
@Data

public class HelixRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private java.util.Map<String, Object> helixMap;
}
