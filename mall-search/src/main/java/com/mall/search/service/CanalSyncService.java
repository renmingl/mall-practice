package com.mall.search.service;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.mall.search.document.ProductDoc;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canal binlog 增量同步（阶段 8 13.1）：订阅 mall 库 product_spu 表变更 → 同步 ES 商品索引
 * 开关 elasticsearch.canal.enabled=false 默认关闭（需 docker compose --profile search 起 Canal Server）；
 * 变更处理：INSERT/UPDATE 按 spuId 重查 DB 组装文档 upsert，DELETE 直接删文档；
 * 说明：product_sku 价格/上下架变更不单独订阅（低频），由 reindex 全量兜底
 * @author renmingl
 * @date 2026-09-01 16:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanalSyncService {

    private final ProductIndexService indexService;

    @Value("${elasticsearch.canal.enabled:false}")
    private boolean enabled;
    @Value("${elasticsearch.canal.host:127.0.0.1}")
    private String host;
    @Value("${elasticsearch.canal.port:11111}")
    private int port;
    @Value("${elasticsearch.canal.destination:example}")
    private String destination;
    @Value("${elasticsearch.canal.username:}")
    private String username;
    @Value("${elasticsearch.canal.password:}")
    private String password;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private CanalConnector connector;
    private Thread worker;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Canal 增量同步未启用（elasticsearch.canal.enabled=false），商品索引仅 reindex 全量维护");
            return;
        }
        running.set(true);
        worker = new Thread(this::loop, "canal-sync-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("Canal 增量同步启动：{}:{} destination={}", host, port, destination);
    }

    /** 消费线程：拉取 binlog 批次 → 逐行处理 → ack；异常批次 rollback 待重试 */
    private void loop() {
        try {
            connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(host, port), destination, username, password);
            connector.connect();
            // Canal filter 语法：库名\\.表名（正则），仅订阅商品主表
            connector.subscribe("mall\\\\.product_spu");
            connector.rollback();
            log.info("Canal 已订阅 mall.product_spu，等待 binlog 事件");
            while (running.get()) {
                Message message = connector.getWithoutAck(100, 100L, TimeUnit.MILLISECONDS);
                long batchId = message.getId();
                if (batchId == -1 || message.getEntries().isEmpty()) {
                    continue;
                }
                try {
                    for (CanalEntry.Entry entry : message.getEntries()) {
                        // 跳过事务 Begin/End 等非行数据条目
                        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                            continue;
                        }
                        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                            handle(rowChange.getEventType(), rowData);
                        }
                    }
                    connector.ack(batchId);
                } catch (Exception e) {
                    log.error("Canal 批次处理失败 batchId={}，回滚待重试", batchId, e);
                    connector.rollback(batchId);
                }
            }
        } catch (Exception e) {
            log.error("Canal 连接异常，同步线程退出（可重启服务恢复）", e);
        } finally {
            if (connector != null) {
                connector.disconnect();
            }
        }
    }

    private void handle(CanalEntry.EventType eventType, CanalEntry.RowData rowData) {
        Long spuId = spuIdOf(rowData);
        if (spuId == null) {
            return;
        }
        switch (eventType) {
            case INSERT, UPDATE -> {
                ProductDoc doc = indexService.loadDoc(spuId);
                if (doc != null) {
                    indexService.upsert(doc);
                    log.info("Canal 增量同步 upsert spuId={}", spuId);
                }
            }
            case DELETE -> {
                indexService.deleteById(spuId);
                log.info("Canal 增量同步 delete spuId={}", spuId);
            }
            default -> {
                // QUERY/TRUNCATE 等忽略
            }
        }
    }

    /** 取行主键：INSERT/UPDATE 用 after 列，DELETE 用 before 列 */
    private Long spuIdOf(CanalEntry.RowData rowData) {
        List<CanalEntry.Column> columns = rowData.getAfterColumnsList().isEmpty()
                ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();
        for (CanalEntry.Column column : columns) {
            if ("id".equals(column.getName())) {
                return Long.valueOf(column.getValue());
            }
        }
        return null;
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }
}
