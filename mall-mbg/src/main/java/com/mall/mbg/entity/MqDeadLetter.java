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
 * MQ 死信表（消费重试耗尽进入 %DLQ%{group} 主题后落库，人工介入/补偿）
 *
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@Getter
@Setter
@ToString
@TableName("mq_dead_letter")
public class MqDeadLetter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 死信ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 消费组（进入死信队列的原始消费组）
     */
    private String consumerGroup;

    /**
     * 主题（%DLQ%{group} 死信主题或原始主题）
     */
    private String topic;

    /**
     * 消息体（原始消息内容）
     */
    private String messageBody;

    /**
     * 失败原因（消费异常信息）
     */
    private String errorInfo;

    /**
     * 状态：0待处理 1已处理
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
