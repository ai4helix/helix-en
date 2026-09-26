package com.helix.console.system.security;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String currentAccount() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getAccount();
    }

    public static Long currentOrganId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getOrganId();
    }

    public static Integer currentUserType() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserType();
    }

    public static boolean isAdminUser() {
        LoginUser u = HOLDER.get();
        return u != null && u.isAdminUser();
    }

    public static boolean isSuperAdmin() {
        LoginUser u = HOLDER.get();
        return u != null && u.isSuperAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
