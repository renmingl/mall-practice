package com.mall.mbg.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;
import java.util.List;

/**
 * MyBatis-Plus 代码生成器（阶段 1：实体/Mapper 落地）
 * 产出物（全部生成到 mall-mbg 模块内，7 个有表服务依赖 mall-mbg 获得自己的实体）：
 * 实体：mall-mbg/src/main/java/com/mall/mbg/entity（Lombok @Data + @TableName）
 * Mapper：mall-mbg/src/main/java/com/mall/mbg/mapper（继承 BaseMapper，无需 XML 即可 CRUD）
 * XML：mall-mbg/src/main/resources/mapper（BaseResultMap / BaseColumnList，便于后续自定义 SQL）
 * 运行方式（两种任选）：
 * <ol>
 * IDEA 直接运行本类 main
 * 命令行：{@code mvn -pl mall-mbg exec:java}（根目录执行，exec-maven-plugin 已配置）
 * </ol>
 * 注意：本类仅生成期使用，不属于运行时代码；数据库连接账密按本机环境修改。
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
public class MyBatisPlusGenerator {

    /** 数据库连接（与各服务 application.yml 保持一致，请按本机环境修改账密） */
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/mall"
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "123456";

    /** 输出根目录：mall-mbg 模块 src/main（要求从仓库根目录运行，否则用绝对路径） */
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + "/mall-mbg/src/main";

    /** mall.sql 全部 28 张表（表前缀 = 数据语义域，与 README「业务表设计总览」一致） */
    private static final List<String> TABLES = List.of(
            // 后台管理域（mall-auth 持有）
            "admin_user", "admin_role", "admin_menu", "admin_user_role", "admin_role_menu",
            // 会员域（mall-member）
            "member", "member_address", "member_point_log", "member_favorite",
            // 商品域 + 进销存域（mall-product）
            "product_category", "product_brand", "product_spu", "product_sku", "product_stock_log",
            "product_comment", "product_supplier", "product_purchase", "product_purchase_item",
            // 订单域（mall-order）
            "orders", "order_item", "order_status_log",
            // 支付域（mall-payment）
            "payment", "payment_refund",
            // 营销域（mall-coupon）
            "coupon", "coupon_user",
            // 秒杀域（mall-seckill）
            "seckill_session", "seckill_product",
            // 公共域（事务消息，由 order/payment 使用）
            "tx_message");

    public static void main(String[] args) {
        FastAutoGenerator.create(URL, USERNAME, PASSWORD)
                .globalConfig(builder -> builder
                        .author("renmingl")
                        // 注释日期格式带时分秒（@date 渲染为 yyyy-MM-dd HH:mm:ss，与手写类注释规范一致）
                        .commentDate("yyyy-MM-dd HH:mm:ss")
                        .outputDir(OUTPUT_DIR + "/java")
                        .disableOpenDir())
                .packageConfig(builder -> builder
                        .parent("com.mall.mbg")
                        .entity("entity")
                        .mapper("mapper")
                        // XML 单独落到 resources/mapper，避免打进 java 包目录
                        .pathInfo(Collections.singletonMap(OutputFile.xml, OUTPUT_DIR + "/resources/mapper")))
                // 自定义模板：类注释去 HTML 标签、@date 标注时间（与手写类注释规范一致）
                .templateConfig(builder -> builder
                        .entity("/templates/entity.java.ftl")
                        .mapper("/templates/mapper.java.ftl"))
                .strategyConfig(builder -> builder
                        .addInclude(TABLES)
                        .entityBuilder()
                        .enableLombok()
                        // product_sku.version → @Version（乐观锁防超卖，README 场景 5.2）
                        .versionColumnName("version")
                        .mapperBuilder()
                        .enableBaseResultMap()
                        .enableBaseColumnList()
                        // 仅生成 entity/mapper，Service/Controller 由各业务模块手写（骨架阶段不生成）
                        // 注意：生成器不覆盖已存在文件，重复生成前先删除 entity/mapper/xml 产物目录
                        .serviceBuilder()
                        .disableService()
                        .disableServiceImpl()
                        .controllerBuilder()
                        .disable())
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
        System.out.println("生成完成：共 " + TABLES.size() + " 张表 → " + OUTPUT_DIR);
    }
}
