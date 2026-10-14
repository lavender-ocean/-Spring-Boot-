package com.sky.constant;

/**
 * 角色常量
 */
public class RoleConstant {

    /** 超级管理员：拥有所有权限 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 订单操作员：只能处理订单 */
    public static final String ORDER_OPERATOR = "ORDER_OPERATOR";

    /** 菜品操作员：只能管理菜品 */
    public static final String DISH_OPERATOR = "DISH_OPERATOR";

    /** 数据观察员：只能看数据 */
    public static final String DATA_VIEWER = "DATA_VIEWER";

    /** 普通员工：无特殊权限 */
    public static final String EMPLOYEE = "EMPLOYEE";
}