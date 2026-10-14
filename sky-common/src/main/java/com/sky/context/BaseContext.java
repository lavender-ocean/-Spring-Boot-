package com.sky.context;

public class BaseContext {

//    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
//
//    public static void removeCurrentId() {
//        threadLocal.remove();
//    }
//

    //`BaseContext` 是**基于 ThreadLocal 实现的线程级用户上下文容器**，
    // 在一次接口请求链路里，存放当前登录用户信息（userId、userType）；
    // **同一个线程内随处可取，不同线程互相隔离**，核心解决：不用每层接口透传 userId 参数。

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /**
     * 设置完整上下文
     *
     */
    public static void set(UserContext ctx){
        CONTEXT.set(ctx);
    }


    /**
     * 获取完整上下文
     */
    public static UserContext get(){
        return CONTEXT.get();
    }


    /**
     * 获取 userId
     * @return
     */
    public static Long getCurrentId() {

        UserContext ctx = CONTEXT.get();
        if(ctx == null){
            return null;
        }
        return ctx.getUserId();

    }

    /**
     * 设置 userId
     */
    public static void setCurrentId(Long id){
        UserContext ctx = CONTEXT.get();
        if (ctx == null) {
            ctx = new UserContext();
            CONTEXT.set(ctx);
        }
        ctx.setUserId(id);

    }

    /**
     * 获取当前的身份类型
     */
    public static String getUserType(){
        UserContext ctx = CONTEXT.get();
        if(ctx == null){
            return null;
        }
        return ctx.getUserType();
    }


    /**
     * 清理（重要！线程池复用会读到脏数据）
     */
    public static void remove(){

        CONTEXT.remove();
    }


}
