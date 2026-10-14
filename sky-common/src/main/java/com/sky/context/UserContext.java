//package com.sky.context;
//
//
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//@NoArgsConstructor
//@AllArgsConstructor
//
//@Data
//public class UserContext {
//
//
//    //标志 --- 当前的用户ID （员工或 C 端用户）
//    private Long userId;
//
//    //身份类型：EMP / USER
//    private String UserType;
//
//}


/**
 * 进行 RABC 的 扩展
 *
 */


package com.sky.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户的完整上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    /** 用户 ID（员工或 C 端用户） */
    private Long userId;
    /** 身份类型：EMP / USER */
    private String userType;
    /** 角色标识（仅管理端有值）：SUPER_ADMIN / ORDER_OPERATOR... */
    private String role;

    /**
     * 便捷构造器（2 参数，无角色） ------ 如果是 普通用户 登陆过来 ，那就不需要 role 的 属性！！！！！！
     * 兼容 C 端用户场景（C 端没有 RBAC 角色）
     */
    public UserContext(Long userId, String userType) {


        this(userId, userType, null);
    }
}