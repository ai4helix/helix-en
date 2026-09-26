package com.helix.facade.feature;

import lombok.Data;

import java.io.Serializable;

/**
 * A single feature item: key = engine feature name, value = value returned by the channel.
 */
@Data
public class DataItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key;

    private Object value;
}
