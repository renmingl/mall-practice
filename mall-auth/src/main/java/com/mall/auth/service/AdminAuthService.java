package com.mall.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.dto.LoginResponse;
import com.mall.auth.security.SecurityService;
import com.mall.auth.util.JwtUtil;
import com.mall.common.exception.BizException;
import com.mall.common.result.ResultCode;
import com.mall.common.util.PasswordUtil;
import com.mall.mbg.entity.AdminMenu;
import com.mall.mbg.entity.AdminRole;
import com.mall.mbg.entity.AdminRoleMenu;
import com.mall.mbg.entity.AdminUser;
import com.mall.mbg.entity.AdminUserRole;
import com.mall.mbg.mapper.AdminMenuMapper;
import com.mall.mbg.mapper.AdminRoleMapper;
import com.mall.mbg.mapper.AdminRoleMenuMapper;
import com.mall.mbg.mapper.AdminUserMapper;
import com.mall.mbg.mapper.AdminUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理员认证（1.7 前后台账号分离：admin_user RBAC 权限模型，与买家 member 扁平权益模型隔离）
 * 登录 → 加载角色/权限 → 权限标识写入 JWT（网关透传 X-User-Perms 供 @PreAuthorize 校验）
 * @author renmingl
 * @date 2026-08-26 11:04:10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    /** 超级管理员角色编码（权限标识写 "*"，全接口放行） */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminMenuMapper adminMenuMapper;
    private final JwtUtil jwtUtil;
    private final AuthRedisService authRedisService;

    /** 后台登录：图形验证码 → BCrypt 校验 → 加载权限 → 签发双令牌（type=ADMIN，perms 写入 JWT） */
    public LoginResponse login(String username, String password, String captchaUuid, String captchaCode) {
        // 图形验证码前置校验（防暴力破解；与买家登录同一验证码体系）
        authRedisService.verifyCaptcha(captchaUuid, captchaCode);
        AdminUser adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (adminUser == null || !PasswordUtil.matches(password, adminUser.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (adminUser.getStatus() == null || adminUser.getStatus() != 1) {
            throw new BizException("账号已被禁用，请联系超级管理员");
        }
        // 更新最后登录时间
        adminUser.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(adminUser);
        log.info("后台管理员登录成功：username={}, adminId={}", username, adminUser.getId());

        List<String> roles = loadRoles(adminUser.getId());
        List<String> perms = loadPerms(adminUser.getId(), roles);
        String accessToken = jwtUtil.createAccessToken(adminUser.getId(), adminUser.getUsername(),
                JwtUtil.USER_TYPE_ADMIN, String.join(",", perms));
        String refreshToken = jwtUtil.createRefreshToken(adminUser.getId(), JwtUtil.USER_TYPE_ADMIN);
        authRedisService.saveRefreshToken(jwtUtil.getJti(jwtUtil.parse(refreshToken)), JwtUtil.USER_TYPE_ADMIN);
        // 登记用户令牌集合（踢下线支持）
        authRedisService.trackTokens(JwtUtil.USER_TYPE_ADMIN, adminUser.getId(),
                jwtUtil.getJti(jwtUtil.parse(accessToken)), jwtUtil.getJti(jwtUtil.parse(refreshToken)));

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtUtil.getAccessExpireSeconds());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", adminUser.getId());
        user.put("username", adminUser.getUsername());
        user.put("nickname", adminUser.getNickname());
        user.put("avatar", adminUser.getAvatar());
        user.put("userType", JwtUtil.USER_TYPE_ADMIN);
        user.put("roles", roles);
        user.put("perms", perms);
        response.setUser(user);
        return response;
    }

    /** 当前登录管理员信息（刷新页面恢复登录态用） */
    public Map<String, Object> me(Long adminId) {
        AdminUser adminUser = adminUserMapper.selectById(adminId);
        if (adminUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在或已删除");
        }
        List<String> roles = loadRoles(adminId);
        List<String> perms = loadPerms(adminId, roles);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", adminUser.getId());
        data.put("username", adminUser.getUsername());
        data.put("nickname", adminUser.getNickname());
        data.put("avatar", adminUser.getAvatar());
        data.put("roles", roles);
        data.put("perms", perms);
        return data;
    }

    /** 用户角色编码列表（仅启用角色） */
    public List<String> loadRoles(Long adminId) {
        List<Long> roleIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId))
                .stream().map(AdminUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return adminRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .map(AdminRole::getCode)
                .toList();
    }

    /** 权限标识列表：超级管理员 → ["*"]，否则按 角色→菜单 权限树取 perms */
    public List<String> loadPerms(Long adminId, List<String> roles) {
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return List.of(SecurityService.ALL_PERM);
        }
        List<Long> roleIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId))
                .stream().map(AdminUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> menuIds = adminRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<AdminRoleMenu>().in(AdminRoleMenu::getRoleId, roleIds))
                .stream().map(AdminRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        return adminMenuMapper.selectBatchIds(menuIds).stream()
                .map(AdminMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
