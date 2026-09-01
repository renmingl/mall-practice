package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.SeckillSession;
import com.mall.mbg.mapper.SeckillSessionMapper;
import com.mall.seckill.dto.SessionSaveDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀场次服务（14.1）：后台 CRUD/启停 + 前台场次列表（含进行中状态）
 * 状态判断：status=1 且 start<=now<=end 为进行中；预热/清理由 SeckillTask 定时驱动
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillSessionService {

    private final SeckillSessionMapper sessionMapper;

    /** 后台分页（状态/关键字筛选，按开始时间倒序） */
    public Page<Map<String, Object>> adminPage(String keyword, Integer status, long page, long size) {
        Page<SeckillSession> sessionPage = sessionMapper.selectPage(new Page<>(page, size),
                Wrappers.<SeckillSession>lambdaQuery()
                        .like(keyword != null && !keyword.isBlank(), SeckillSession::getName, keyword)
                        .eq(status != null, SeckillSession::getStatus, status)
                        .orderByDesc(SeckillSession::getStartTime));
        Page<Map<String, Object>> result = new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        result.setRecords(sessionPage.getRecords().stream().map(s -> toRow(s, true)).toList());
        return result;
    }

    /** 前台场次列表：启用场次按开始时间升序（含 id 与当前状态标记，供会场页切换场次） */
    public List<Map<String, Object>> listActive() {
        return sessionMapper.selectList(Wrappers.<SeckillSession>lambdaQuery()
                        .eq(SeckillSession::getStatus, 1)
                        .orderByAsc(SeckillSession::getStartTime))
                .stream().map(s -> toRow(s, true)).toList();
    }

    /** 即将开始且未过期的启用场次（定时预热扫描用：startTime <= end 且 startTime >= start） */
    public List<SeckillSession> listEnabledSessions(LocalDateTime end, LocalDateTime start) {
        return sessionMapper.selectList(Wrappers.<SeckillSession>lambdaQuery()
                .eq(SeckillSession::getStatus, 1)
                .le(SeckillSession::getStartTime, end)
                .ge(SeckillSession::getStartTime, start));
    }

    /** 保存场次（新增/修改；结束时间须晚于开始时间） */
    @Transactional(rollbackFor = Exception.class)
    public void save(SessionSaveDTO dto) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        SeckillSession session = dto.getId() == null ? new SeckillSession() : getById(dto.getId());
        session.setName(dto.getName());
        session.setStartTime(dto.getStartTime());
        session.setEndTime(dto.getEndTime());
        if (dto.getStatus() != null) {
            session.setStatus(dto.getStatus());
        }
        if (session.getId() == null) {
            session.setStatus(dto.getStatus() == null ? (byte) 1 : dto.getStatus());
            sessionMapper.insert(session);
        } else {
            sessionMapper.updateById(session);
        }
        log.info("保存秒杀场次 id={} name={} {} ~ {}", session.getId(), session.getName(),
                session.getStartTime(), session.getEndTime());
    }

    /** 启停场次（禁用中场次前端不可见、定时任务跳过） */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Byte status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态仅支持 0禁用 / 1启用");
        }
        SeckillSession session = getById(id);
        session.setStatus(status);
        sessionMapper.updateById(session);
        log.info("切换场次状态 id={} status={}", id, status);
    }

    /** 按 ID 查场次（不存在抛异常） */
    public SeckillSession getById(Long id) {
        SeckillSession session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BizException("场次不存在");
        }
        return session;
    }

    /** 场次进行中（status=1 且在时间窗口内） */
    public boolean isOngoing(SeckillSession session) {
        LocalDateTime now = LocalDateTime.now();
        return session.getStatus() == 1
                && !now.isBefore(session.getStartTime())
                && !now.isAfter(session.getEndTime());
    }

    private Map<String, Object> toRow(SeckillSession s, boolean withId) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (withId) {
            row.put("id", s.getId());
        }
        row.put("name", s.getName());
        row.put("startTime", s.getStartTime());
        row.put("endTime", s.getEndTime());
        row.put("status", s.getStatus());
        LocalDateTime now = LocalDateTime.now();
        String phase;
        if (s.getStatus() == 0) {
            phase = "disabled";
        } else if (now.isBefore(s.getStartTime())) {
            phase = "upcoming";
        } else if (now.isAfter(s.getEndTime())) {
            phase = "finished";
        } else {
            phase = "ongoing";
        }
        row.put("phase", phase);
        return row;
    }
}
