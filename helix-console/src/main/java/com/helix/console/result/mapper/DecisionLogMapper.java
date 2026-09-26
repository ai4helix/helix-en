package com.helix.console.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.result.entity.DecisionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DecisionLogMapper extends BaseMapper<DecisionLog> {

    @Select("SELECT organ_id AS organId, COUNT(*) AS total, " +
            "SUM(CASE WHEN result_type = '1' THEN 1 ELSE 0 END) AS passCnt, " +
            "SUM(CASE WHEN result_type = '2' THEN 1 ELSE 0 END) AS rejectCnt, " +
            "SUM(CASE WHEN result_type = '3' THEN 1 ELSE 0 END) AS manualCnt, " +
            "MAX(created_time) AS lastTime " +
            "FROM t_decision_log WHERE deleted = 0 AND shadow = 0 GROUP BY organ_id")
    List<Map<String, Object>> statsByOrgan();

    @Select("SELECT organ_id AS organId, COUNT(*) AS total " +
            "FROM t_decision_log WHERE deleted = 0 AND shadow = 0 " +
            "AND created_time >= CURDATE() GROUP BY organ_id")
    List<Map<String, Object>> statsTodayByOrgan();

    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN created_time >= CURDATE() THEN 1 ELSE 0 END) AS todayTotal " +
            "FROM t_decision_log WHERE deleted = 0 AND shadow = 0")
    Map<String, Object> statsTotal();
}
