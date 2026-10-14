package com.sky.aspect;


import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 *
 */


@Aspect
@Component
@Slf4j

public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("(execution(* com.sky.mapper.*.*(..)) || execution(* com.vueUser.mapper.*.*(..))) " +
            "&& @annotation(com.sky.annotation.AutoFill)")
//    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFIllPointCut(){


    }

    //前置通知，在通知中进行公共字段的赋值
    @Before("autoFIllPointCut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        log.info("开始进行公共字段的自动填充....");

        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();

        AutoFill autoFill = methodSignature.getMethod().getAnnotation(AutoFill.class);

        OperationType operationType = autoFill.value();


        Object[] args = joinPoint.getArgs();

        if(args.length == 0){
            return;
        }

        Object entity = args[0];

        LocalDateTime localDateTime = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();


        if(operationType == OperationType.INSERT){
            Method setCreateTime = entity.getClass().getDeclaredMethod("setCreateTime",LocalDateTime.class);
            Method setCreateUser = entity.getClass().getDeclaredMethod("setCreateUser",Long.class);
            Method setUpdateTime = entity.getClass().getDeclaredMethod("setUpdateTime",LocalDateTime.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod("setUpdateUser",Long.class);


            setCreateTime.invoke(entity,localDateTime);
            setCreateUser.invoke(entity,currentId);
            setUpdateTime.invoke(entity,localDateTime);
            setUpdateUser.invoke(entity,currentId);

        }else if(operationType == OperationType.UPDATE){

            Method setUpdateTime = entity.getClass().getDeclaredMethod("setUpdateTime",LocalDateTime.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod("setUpdateUser",Long.class);
            setUpdateTime.invoke(entity,localDateTime);
            setUpdateUser.invoke(entity,currentId);
        }



    }





}




















