package com.mall.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.mbg.entity.Member;
import com.mall.mbg.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会员运营数据（场景 10.1 / 10.3，纯 Redis）：
 * 在线：ZSET online_users（member=userId，score=最近活跃时间戳），ZCOUNT 统计 5 分钟在线，定时清理离线；
 * 日活：Bitmap active:{yyyyMMdd}（登录成功 SETBIT）；签到：Bitmap sign:{memberId}:{yyyyMM}（当日位置 1）
 * + checkin:{yyyyMMdd}（今日签到用户全局 Bitmap，BITCOUNT 即今日签到人数）
 * 为什么不用 MySQL 计数：在线/签到高频写，Bitmap 单 bit 写入 + BITCOUNT 聚合，内存占用低（万级用户 KB 级）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberStatsService {

    /** 在线用户 ZSET（score=最近活跃时间戳，毫秒） */
    public static final String KEY_ONLINE = "online_users";

    /** 日活 Bitmap 前缀：active:{yyyyMMdd} */
    public static final String KEY_DAILY_ACTIVE = "active:";

    /** 个人签到 Bitmap 前缀：sign:{memberId}:{yyyyMM} */
    public static final String KEY_SIGN = "sign:";

    /** 今日签到用户全局 Bitmap：checkin:{yyyyMMdd} */
    public static final String KEY_CHECKIN = "checkin:";

    /** 在线统计窗口：5 分钟内活跃视为在线 */
    public static final long ONLINE_WINDOW_SECONDS = 300;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;
    private final MemberMapper memberMapper;

    /** 登录成功记录在线 + 日活（auth 编排登录/注册后调用；在线以最近活跃时间戳为 score，天然滑动窗口） */
    public void recordActive(Long memberId) {
        if (memberId == null) {
            return;
        }
        redisTemplate.opsForZSet().add(KEY_ONLINE, String.valueOf(memberId), System.currentTimeMillis());
        redisTemplate.opsForValue().setBit(KEY_DAILY_ACTIVE + today(), memberId, true);
    }

    /** 签到（10.3）：个人 Bitmap 当日位置 1 + 今日签到全局 Bitmap；返回签到状态/当月天数/连续天数 */
    public Map<String, Object> checkin(Long memberId) {
        String month = today().substring(0, 6);
        String signKey = KEY_SIGN + memberId + ":" + month;
        int day = Integer.parseInt(today().substring(6));
        boolean firstToday = !Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(signKey, day));
        if (firstToday) {
            redisTemplate.opsForValue().setBit(signKey, day, true);
            redisTemplate.opsForValue().setBit(KEY_CHECKIN + today(), memberId, true);
            log.info("会员签到 memberId={} date={}", memberId, today());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", today());
        row.put("signedToday", !firstToday);
        row.put("monthDays", monthDays(signKey));
        row.put("streakDays", streakDays(signKey, day));
        return row;
    }

    /** 签到状态查询（不产生写入）：当月天数 + 今天是否已签 + 连续天数 */
    public Map<String, Object> checkinStatus(Long memberId) {
        String month = today().substring(0, 6);
        String signKey = KEY_SIGN + memberId + ":" + month;
        int day = Integer.parseInt(today().substring(6));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", today());
        row.put("signedToday", Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(signKey, day)));
        row.put("monthDays", monthDays(signKey));
        row.put("streakDays", streakDays(signKey, day));
        return row;
    }

    /** 实时在线人数（10.1）：5 分钟窗口内活跃用户数 */
    public long onlineCount() {
        long windowStart = System.currentTimeMillis() - ONLINE_WINDOW_SECONDS * 1000;
        Long count = redisTemplate.opsForZSet().count(KEY_ONLINE, windowStart, Long.MAX_VALUE);
        return count == null ? 0 : count;
    }

    /** 清理离线用户（定时，防 ZSET 无限膨胀；窗口外 score 移除） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cleanupOnline() {
        long windowStart = System.currentTimeMillis() - ONLINE_WINDOW_SECONDS * 1000;
        Long removed = redisTemplate.opsForZSet().removeRangeByScore(KEY_ONLINE, 0, windowStart);
        if (removed != null && removed > 0) {
            log.info("清理离线用户 count={}", removed);
        }
    }

    /** 日活（10.3）：指定日（yyyyMMdd，缺省今天）活跃用户数 BITCOUNT */
    public long dau(String date) {
        String day = date == null || date.isBlank() ? today() : date;
        return bitCount(KEY_DAILY_ACTIVE + day);
    }

    /** 今日签到人数（BITCOUNT checkin:{yyyyMMdd}） */
    public long checkinToday() {
        return bitCount(KEY_CHECKIN + today());
    }

    /** 今日新增注册会员数（看板概览：member.create_time >= 今日 0 点） */
    public long newMembersToday() {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
                .ge(Member::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    /** 指定会员当月签到天数（看板会员统计） */
    public long checkinMonthDays(Long memberId, String month) {
        if (month == null || month.isBlank()) {
            month = today().substring(0, 6);
        }
        return bitCount(KEY_SIGN + memberId + ":" + month);
    }

    private long monthDays(String signKey) {
        return bitCount(signKey);
    }

    /** BITCOUNT：统计 Bitmap 置位个数（经 execute 回调，ValueOperations 无此方法） */
    private long bitCount(String key) {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.bitCount(key.getBytes()));
        return count == null ? 0 : count;
    }

    /** 连续签到天数（从今天往前逐位回看，遇 0 停止） */
    private int streakDays(String signKey, int today) {
        int streak = 0;
        for (int i = today; i >= 1; i--) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(signKey, i))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private String today() {
        return LocalDate.now().format(DAY);
    }
}
