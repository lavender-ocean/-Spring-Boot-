//package com.sky.constant;
//
//public class JwtClaimsConstant {
//
//    public static final String EMP_ID = "empId";
//    public static final String USER_ID = "userId";
//    public static final String PHONE = "phone";
//    public static final String USERNAME = "username";
//    public static final String NAME = "name";
//
//}


package com.sky.constant;

public class JwtClaimsConstant {

    //员工
    public static final String EMP_ID = "empId";
    //用户
    public static final String USER_ID = "userId";
    public static final String USER_TYPE = "userType";    // 类型
    public static final String PHONE = "phone";
    public static final String USERNAME = "username";
    public static final String NAME = "name";

    //  身份类型常量（避免硬编码字符串）
    public static final String TYPE_ADMIN = "EMP";        // 后台员工
    public static final String TYPE_USER  = "USER";       // C 端用户
    public static final String ROLE = "role";    // 员工 身份

}