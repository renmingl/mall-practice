package com.mall.ai.model;

/**
 * 当前请求用户（阶段 9 16.3 登录态分层）：由网关透传头解析
 * 游客：userId / userType 均为 null（仅普通问答 + 知识库，不落库）
 * 买家：userType=MEMBER，userId=会员 ID（可查本人数据，portal 场景）
 * 管理员：userType=ADMIN，userId=管理员 ID（可查管理侧数据，admin 场景）
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
public record AiUser(Long userId, String userType) {

    /** 是否登录（游客无用户上下文） */
    public boolean isLoggedIn() {
        return userId != null;
    }

    /** 是否管理员 */
    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }
}
