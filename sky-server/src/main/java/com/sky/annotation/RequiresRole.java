package com.sky.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 权限校验注解
 * 用在 Controller 方法上，声明"访问该接口需要哪些角色"
 * 示例：@RequiresRole({RoleConstant.SUPER_ADMIN, RoleConstant.ORDER_OPERATOR})
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {


    /**
     * 允许访问的角色列表（任一满足即可）
     */
// 属性：value，字符串数组，接收角色编码；满足其中任意一个角色就放行
    String[] value();
}
