package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.ListEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * List data entry Mapper (engine read-only: loaded into the snapshot, zero DB queries on the decision path).
 */
@Mapper
public interface ListEntryMapper extends BaseMapper<ListEntryEntity> {

    /**
     * Enabled list entries within their validity period (only enabled list DBs).
     *
     * <p>Validity semantics: null effective_from means effective immediately; null effective_to means valid indefinitely.</p>
     */
    @Select("select e.* from t_list_entry e "
            + "join t_list_db d on d.id = e.list_id and d.status = 1 "
            + "where e.deleted = 0 and e.status = 1 "
            + "and (e.effective_from is null or e.effective_from <= #{now}) "
            + "and (e.effective_to is null or e.effective_to >= #{now})")
    List<ListEntryEntity> selectEnabledEntries(@Param("now") LocalDateTime now);

    /** Ids and names of enabled list DBs (for trace display, row keys id / list_name) */
    @Select("select id, list_name from t_list_db where status = 1")
    List<java.util.Map<String, Object>> selectEnabledListNames();

    /**
     * The next list validity window boundary: the <b>future</b> min effective_from and effective_to among
     * enabled list entries.
     *
     * <p>List entries take effect/expire by time window without generating any write events. The engine uses
     * this to proactively perform a lightweight list reload at the boundary moment, avoiding "not effective
     * when due"; returns null when there is no future boundary.</p>
     */
    @Select("select min(t) from ("
            + "select min(effective_from) t from t_list_entry "
            + "where deleted = 0 and status = 1 and effective_from is not null and effective_from > #{now} "
            + "union all "
            + "select min(effective_to) t from t_list_entry "
            + "where deleted = 0 and status = 1 and effective_to is not null and effective_to > #{now}"
            + ") x")
    LocalDateTime selectNextEffectiveBoundary(@Param("now") LocalDateTime now);
}
