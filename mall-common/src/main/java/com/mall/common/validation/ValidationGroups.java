package com.mall.common.validation;

/**
 * 参数校验分组（JSR-303 @Validated 分组校验）
 * 场景：同一 DTO 在新增 / 修改接口的校验规则不同，如新增时 id 可为空、修改时必填——
 * 字段注解指定 groups，接口入参用 {@code @Validated(ValidationGroups.AddGroup.class)} 按组生效
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
public interface ValidationGroups {

    /** 新增分组 */
    interface AddGroup {
    }

    /** 修改分组 */
    interface UpdateGroup {
    }
}
