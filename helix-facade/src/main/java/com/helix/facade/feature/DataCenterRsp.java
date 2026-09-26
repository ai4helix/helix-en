package com.helix.facade.feature;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Feature data response: rspCode=00 means success.
 */
@Data
public class DataCenterRsp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String rspCode;

    private List<DataItem> rspData;
}
