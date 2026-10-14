package com.sky.aspect;


import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import com.sky.context.BaseContext;
import com.sky.context.UserContext;
import com.sky.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色 权限 校验 切面类
 *
 */

@Slf4j
@Aspect
@Component
public class RoleAspect {


    @Before("@annotation(requiresRole)")
    public void checkRole(JoinPoint joinPoint, RequiresRole requiresRole) {

        //1. 获取当前 用户上下文 ---
        UserContext ctx = BaseContext.get();
        if(ctx == null || ctx.getUserType() == null){

            throw new ForbiddenException("未登录");
        }


        // 2. C 端用户直接拒绝（C 端没有 RBAC） ----- 神了
        if (!"EMP".equals(ctx.getUserType())) {


            throw new ForbiddenException("无权限访问管理端接口");
        }

        //3.super_admin 方行 直接给最高权限
        if(RoleConstant.SUPER_ADMIN.equals(ctx.getRole())){
            return;
        }

        //4.进行校验 ---- 校验角色是否匹配
        //先把 注解的 属性 拿过来
        String[] roles = requiresRole.value();
        //再把当前的 登录的人 的 role 拿过来
        String currentRole = ctx.getRole();
//        判断权限问题 --- 当前的 requireRole【】 中 是否 包含了 目前登录 的这个人


        log.info("当前的===== requiresRole={}",Arrays.toString(roles));
        log.info("当前登陆的人是===== currentRole={}",currentRole);


        boolean allowed = Arrays.asList(roles).contains(currentRole);


        if(!allowed){

            //权限不足 =============
            log.warn("权限不足，userId = {}, 当前角色 = {}, 需要角色 = {}",
                    ctx.getUserId(), currentRole, Arrays.toString(roles));
            throw new ForbiddenException("权限不足，需要角色：" + Arrays.toString(roles));


        }

    }

}
//
//
//核心逻辑：
//
//没登录 → 拒绝
//
//C 端用户 → 拒绝
//
//超管 → 无条件放行（最高权限）
//
//其他角色 → 必须在注解声明的列表里
