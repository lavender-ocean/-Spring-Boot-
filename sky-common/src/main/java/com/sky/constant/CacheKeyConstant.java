package com.sky.constant;

/**
 * 缓存 Key 命名规范
 * 格式：{模块}:{对象}:{标识}
 *
 * 好处：
 * 1. 避免各处硬编码字符串
 * 2. 统一规范，便于运维排查
 * 3. 改一处，全项目生效
 */
public class CacheKeyConstant {

    // ============ 分类 ============
    /** 分类列表（按 type）：category:list:1 */
    public static final String CATEGORY_LIST_PREFIX = "category:list:";

    // ============ 菜品 ============
    /** 菜品详情：dish:info:65 */
    public static final String DISH_INFO_PREFIX = "dish:info:";
    /** 分类下菜品列表：dish:category:18 */
    public static final String DISH_CATEGORY_PREFIX = "dish:category:";

    // ============ 套餐 ============
    /** 套餐详情：setmeal:info:32 */
    public static final String SETMEAL_INFO_PREFIX = "setmeal:info:";
    /** 分类下套餐列表：setmeal:category:13 */
    public static final String SETMEAL_CATEGORY_PREFIX = "setmeal:category:";

    // ============ 店铺 ============
    /** 店铺状态：shop:status */
    public static final String SHOP_STATUS = "shop:status";

    // ============ 工具方法 ============
    public static String categoryList(Integer type) {
        return CATEGORY_LIST_PREFIX + type;
    }

    public static String dishInfo(Long id) {
        return DISH_INFO_PREFIX + id;
    }

    public static String dishCategory(Long categoryId) {
        return DISH_CATEGORY_PREFIX + categoryId;
    }

    public static String setmealInfo(Long id) {
        return SETMEAL_INFO_PREFIX + id;
    }

    public static String setmealCategory(Long categoryId) {
        return SETMEAL_CATEGORY_PREFIX + categoryId;
    }
}