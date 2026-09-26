package com.helix.console.engine.support;

import com.helix.console.engine.dto.ExecutionResult;
import com.helix.facade.engine.EngineApiRsp;

/**
 * Converter from helix-engine responses to this module's execution result view.
 *
 * <p>Isolates the executor-side structure within the facade contract {@link EngineApiRsp}.
 * The rest of this module only depends on {@link ExecutionResult}; when the executor-side
 * structure changes in the future, only this class needs to change.</p>
 */
public final class ExecutionResultConverter {

    private ExecutionResultConverter() {
    }

    /**
     * Convert.
     *
     * @param data decision result returned by helix-engine
     * @return this module's execution result view
     */
    public static ExecutionResult from(EngineApiRsp.EngineData data) {
        ExecutionResult result = new ExecutionResult();
        if (data == null) {
            return result;
        }
        result.setTraceId(data.getTraceId());
        result.setEngineCode(data.getEngineCode());
        result.setEngineName(data.getEngineName());
        result.setVersionId(data.getVersionId());
        result.setResult(data.getResultType());
        result.setPass(data.isPass());
        result.setManualReview(data.isManualReview());
        result.setScore(data.getScore());
        result.setCostMs(data.getCostMs());
        if (data.getHitRules() != null) {
            result.setHitRules(data.getHitRules());
        }
        if (data.getTraces() != null) {
            result.setTraces(data.getTraces());
        }
        return result;
    }
}
