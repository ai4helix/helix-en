package com.helix.console.engine.service;

import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.entity.EngineVersion;

import java.util.List;

/**
 * Engine and version management.
 */
public interface EngineService {

    /** Engine list of the current organization */
    List<Engine> listEngines(Integer organId);

    /** Valid version list of an engine, running version first */
    List<EngineVersion> listVersions(Integer engineId);

    /** Create an engine */
    Integer createEngine(Engine engine);

    /** Create a draft version */
    Integer createVersion(Integer engineId);
}
