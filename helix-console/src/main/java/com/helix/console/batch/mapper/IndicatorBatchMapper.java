package com.helix.console.batch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.batch.entity.IndicatorBatch;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface IndicatorBatchMapper extends BaseMapper<IndicatorBatch> {

    @Select("SELECT organ_id AS organId, COUNT(*) AS batches, " +
            "IFNULL(SUM(total_rows), 0) AS rowsTotal, " +
            "IFNULL(SUM(success_rows), 0) AS rowsOk " +
            "FROM t_indicator_batch GROUP BY organ_id")
    List<Map<String, Object>> statsByOrgan();
}
