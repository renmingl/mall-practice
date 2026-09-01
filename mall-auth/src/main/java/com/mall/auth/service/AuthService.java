package com.mall.auth.service;

import com.mall.api.member.MemberAccountDTO;
import com.mall.api.member.MemberFeignClient;
import com.mall.api.member.MemberRegisterDTO;
import com.mall.api.member.MemberVerifyResult;
import com.mall.api.member.UpdatePasswordByPhoneDTO;
import com.mall.api.member.VerifyPasswordDTO;
import com.mall.auth.dto.ForgotPasswordRequest;
import com.mall.auth.dto.LoginRequest;
import com.mall.auth.dto.LoginResponse;
import com.mall.auth.dto.RegisterRequest;
import com.mall.auth.util.JwtUtil;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 买家认证编排：注册 / 登录 / 刷新 / 退出 / 找回密码
 * 账号数据在 mall-member（Feign 内部契约），本服务只负责验证码校验与 JWT 签发
 * @author renmingl
 * @date 2026-08-26 18:52:15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberFeignClient memberFeignClient;
    private final JwtUtil jwtUtil;
    private final AuthRedisService authRedisService;

    /** 注册：验证码 → member 建号 → 直接签发双令牌（注册即登录） */
    public LoginResponse register(RegisterRequest request) {
        authRedisService.verifyCaptcha(request.getCaptchaUuid(), request.getCaptchaCode());
        MemberRegisterDTO registerDTO = new MemberRegisterDTO();
        registerDTO.setUsername(request.getUsername());
        registerDTO.setPassword(request.getPassword());
        registerDTO.setNickname(request.getNickname());
        registerDTO.setPhone(request.getPhone());
        registerDTO.setEmail(request.getEmail());
        Result<MemberAccountDTO> result = memberFeignClient.register(registerDTO);
        if (!result.isSuccess()) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        log.info("注册成功并签发令牌：username={}", request.getUsername());
        recordActive(result.getData());
        return buildLoginResponse(result.getData(), JwtUtil.USER_TYPE_MEMBER, null);
    }

    /** 登录：验证码 → member 验密 → 签发双令牌 */
    public LoginResponse login(LoginRequest request) {
        authRedisService.verifyCaptcha(request.getCaptchaUuid(), request.getCaptchaCode());
        VerifyPasswordDTO verifyDTO = new VerifyPasswordDTO();
        verifyDTO.setUsername(request.getUsername());
        verifyDTO.setRawPassword(request.getPassword());
        Result<MemberVerifyResult> result = memberFeignClient.verify(verifyDTO);
        if (!result.isSuccess()) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        MemberVerifyResult verifyResult = result.getData();
        if (!verifyResult.isSuccess()) {
            throw new BizException(verifyResult.getMessage());
        }
        log.info("买家登录成功：username={}", request.getUsername());
        recordActive(verifyResult.getAccount());
        return buildLoginResponse(verifyResult.getAccount(), JwtUtil.USER_TYPE_MEMBER, null);
    }

    /** 刷新：refresh 令牌换新 access（旧 refresh 作废防重放） */
    public LoginResponse refresh(String refreshToken) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        String userType = claims.get(JwtUtil.CLAIM_USER_TYPE, String.class);
        Long userId = claims.get(JwtUtil.CLAIM_USER_ID, Number.class).longValue();
        String jti = jwtUtil.getJti(claims);
        if (!authRedisService.isValidRefreshToken(jti, userType)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        // 轮换：旧 refresh 作废，签发新 refresh
        authRedisService.deleteRefreshToken(jti);
        authRedisService.untrackToken(userType, userId, jti);
        String username = claims.get(JwtUtil.CLAIM_USERNAME, String.class);
        String accessToken = jwtUtil.createAccessToken(userId, username, userType, null);
        String newRefreshToken = jwtUtil.createRefreshToken(userId, userType);
        authRedisService.saveRefreshToken(jwtUtil.getJti(jwtUtil.parse(newRefreshToken)), userType);
        authRedisService.trackTokens(userType, userId, jwtUtil.getJti(jwtUtil.parse(accessToken)),
                jwtUtil.getJti(jwtUtil.parse(newRefreshToken)));
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(jwtUtil.getAccessExpireSeconds());
        return response;
    }

    /** 退出：用户全部令牌失效（access 进黑名单 + refresh 删除，防止退出后 refresh 还能换新令牌） */
    public void logout(String accessToken) {
        Claims claims = jwtUtil.parse(accessToken);
        if (claims == null) {
            return;
        }
        String userType = claims.get(JwtUtil.CLAIM_USER_TYPE, String.class);
        Long userId = claims.get(JwtUtil.CLAIM_USER_ID, Number.class).longValue();
        authRedisService.invalidateUserTokens(userType, userId);
        log.info("退出登录：userId={}, userType={}", userId, userType);
    }

    /** 找回密码：短信验证码校验 → member 按手机号改密 */
    public void forgotPassword(ForgotPasswordRequest request) {
        authRedisService.verifySmsCode(request.getPhone(), request.getSmsCode());
        UpdatePasswordByPhoneDTO dto = new UpdatePasswordByPhoneDTO();
        dto.setPhone(request.getPhone());
        dto.setNewPassword(request.getNewPassword());
        Result<Void> result = memberFeignClient.updatePasswordByPhone(dto);
        if (!result.isSuccess()) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        log.info("找回密码成功：phone={}", request.getPhone());
    }

    /** 登录/注册成功记录在线 + 日活（10.1/10.3，member 侧 Redis 统计；失败不阻断登录） */
    private void recordActive(MemberAccountDTO account) {
        if (account == null || account.getId() == null) {
            return;
        }
        try {
            memberFeignClient.recordActive(account.getId());
        } catch (Exception e) {
            log.warn("记录在线/日活失败 memberId={}", account.getId(), e);
        }
    }

    /** 组装登录响应（买家 userType=MEMBER；后台登录传 perms，见 AdminAuthService） */
    public LoginResponse buildLoginResponse(MemberAccountDTO account, String userType, String perms) {
        String accessToken = jwtUtil.createAccessToken(account.getId(), account.getUsername(), userType, perms);
        String refreshToken = jwtUtil.createRefreshToken(account.getId(), userType);
        authRedisService.saveRefreshToken(jwtUtil.getJti(jwtUtil.parse(refreshToken)), userType);
        // 登记用户令牌集合（踢下线支持）
        authRedisService.trackTokens(userType, account.getId(),
                jwtUtil.getJti(jwtUtil.parse(accessToken)), jwtUtil.getJti(jwtUtil.parse(refreshToken)));
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtUtil.getAccessExpireSeconds());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.getId());
        user.put("username", account.getUsername());
        user.put("nickname", account.getNickname());
        user.put("avatar", account.getAvatar());
        user.put("phone", account.getPhone());
        user.put("level", account.getLevel());
        user.put("points", account.getPoints());
        user.put("userType", userType);
        user.put("perms", perms);
        response.setUser(user);
        return response;
    }
}
