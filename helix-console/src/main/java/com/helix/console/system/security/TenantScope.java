package com.helix.console.system.security;

import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SaaS tenant scope utility (v4).
 *
 * <p>Tenant semantics: the console-side {@code t_organization.id} is the tenant
 * id (1 = platform operator); in engine configuration tables (t_engine/t_rule/
 * t_scorecard/t_knowledge_tree/t_field/t_list_db/t_decision_table),
 * {@code organ_id = 0} means "platform common" data, visible to all tenants.</p>
 *
 * <p>Visibility rules:</p>
 * <ul>
 *   <li>admin users (user_type=1): see all tenants, no filter injected into
 *       lists; on write they may specify the owning organization, defaulting to
 *       "platform common 0";</li>
 *   <li>SaaS users (user_type=2): see only {@code [0, own organization]}; writes
 *       are forced to their own organization; cross-organization reads/writes
 *       always get {@code FORBIDDEN}.</li>
 * </ul>
 */
public final class TenantScope {

    /** "Platform common" owner value in engine configuration tables */
    public static final long PLATFORM = 0L;

    private TenantScope() {
    }

    /** Whether the current logged-in user is a platform admin user */
    public static boolean isAdmin() {
        LoginUser u = UserContext.get();
        return u != null && u.isAdminUser();
    }

    /**
     * Set of organizations visible to the current user.
     *
     * @return null = admin user, no tenant condition injected into queries;
     *         non-null = only these organ_id values are visible
     */
    public static List<Long> visibleOrgans() {
        if (isAdmin()) {
            return null;
        }
        LoginUser u = UserContext.get();
        Long organ = u == null ? null : u.getOrganId();
        return organ == null
                ? Collections.singletonList(PLATFORM)
                : Arrays.asList(PLATFORM, organ);
    }

    /**
     * Write ownership: admin users use the given value (null -> platform common 0);
     * SaaS users are forced to their own organization.
     */
    public static Long writeOrgan(Long requested) {
        if (isAdmin()) {
            return requested == null ? PLATFORM : requested;
        }
        LoginUser u = UserContext.get();
        if (u == null || u.getOrganId() == null) {
            throw BizException.of(ResultCode.FORBIDDEN, "SaaS user has no organization; writing engine data is forbidden");
        }
        return u.getOrganId();
    }

    /** Integer variant of {@link #writeOrgan(Long)}, for entities whose organId is Integer */
    public static Integer writeOrgan(Integer requested) {
        return writeOrgan(requested == null ? null : requested.longValue()).intValue();
    }

    /**
     * Resource ownership check: throws FORBIDDEN when the current user cannot see the owner.
     * null ownership is treated as "platform common 0" (compatibility with legacy data).
     */
    public static void checkVisible(Long organId) {
        List<Long> organs = visibleOrgans();
        if (organs == null) {
            return;
        }
        long v = organId == null ? PLATFORM : organId;
        if (!organs.contains(v)) {
            throw BizException.of(ResultCode.FORBIDDEN, "No permission to access data of other organizations");
        }
    }

    /** Whether the given owner is visible to the current user */
    public static boolean isVisible(Long organId) {
        List<Long> organs = visibleOrgans();
        if (organs == null) {
            return true;
        }
        return organs.contains(organId == null ? PLATFORM : organId);
    }
}
