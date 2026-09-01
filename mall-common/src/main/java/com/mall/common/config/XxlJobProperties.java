package com.mall.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * xxl-job 执行器配置绑定（xxl.job.*）：与 xxl-job 官方执行器配置格式一致
 * （xxl.job.admin.addresses / xxl.job.accessToken / xxl.job.executor.*）。
 * 各服务 application.yml 配置后由 XxlJobAutoConfiguration 装配执行器；
 * 未配置 admin.addresses 的服务不注册执行器（任务仅走本地 @Scheduled 兜底）。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /** 调度中心 */
    private Admin admin = new Admin();

    /** 调度中心通信令牌（与 xxl-job-admin 的 accessToken 一致，默认 default_token） */
    private String accessToken = "default_token";

    /** 本服务执行器 */
    private Executor executor = new Executor();

    public static class Admin {

        /** 调度中心地址（多个逗号分隔，如 http://127.0.0.1:9080/xxl-job-admin） */
        private String addresses;

        public String getAddresses() {
            return addresses;
        }

        public void setAddresses(String addresses) {
            this.addresses = addresses;
        }
    }

    public static class Executor {

        /** 执行器 AppName：调度中心"执行器管理"按此识别，需与预置的 xxl_job_group.app_name 一致 */
        private String appname;

        /** 执行器 IP：留空自动获取 */
        private String ip;

        /** 执行器注册端口（各服务独立端口，避免同机冲突） */
        private int port = 9999;

        /** 执行器日志目录 */
        private String logPath = "./logs/xxl-job";

        /** 执行器日志保留天数 */
        private int logRetentionDays = 7;

        public String getAppname() {
            return appname;
        }

        public void setAppname(String appname) {
            this.appname = appname;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            this.logRetentionDays = logRetentionDays;
        }
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }
}
