package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 助手对话消息表（阶段 9：问答历史入库，游客不落库）
 *
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Getter
@Setter
@ToString
@TableName("ai_chat_message")
public class AiChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 场景：admin 后台助手 / portal 前台客服
     */
    private String scene;

    /**
     * 会话ID（UUID；同一会话多轮共享，登录用户按 会话粒度加载历史）
     */
    private String sessionId;

    /**
     * 用户ID（admin 场景=管理员ID，portal 场景=会员ID；游客不落库）
     */
    private Long userId;

    /**
     * 用户类型：ADMIN / MEMBER（游客不落库）
     */
    private String userType;

    /**
     * 消息角色：user 用户提问 / assistant 模型回答
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
