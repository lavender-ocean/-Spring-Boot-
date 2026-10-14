package com.sky.controller.Token;


import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.RefreshTokenDTO;
import com.sky.entity.Employee;
import com.sky.exception.TokenInvalidException;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.token.RefreshTokenService;
import com.sky.utils.JwtUtil;
import com.sky.vo.TokenPairVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.websocket.server.PathParam;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * 刷新令牌接口
 */
@RestController
@RequestMapping("/refresh")
@Slf4j
@Api(tags = "令牌刷新")
public class RefreshTokenController {


    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 刷新令牌
     * 支持两端：/refresh?scope=emp 或 /refresh?scope=user
     */
    @PostMapping
    @ApiOperation("刷新令牌")
    public Result<TokenPairVO> refresh(
            @RequestParam String scope,
            @RequestBody RefreshTokenDTO dto
    ) throws Exception {
        //判断是否合法 scope
        if (!"emp".equals(scope) && !"user".equals(scope)) {
            throw new TokenInvalidException("scope参数非法，仅支持emp/user");
        }

        String refreshToken = dto.getRefreshToken();

        //判断 不能 为空
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new TokenInvalidException("refreshToken 为空");
        }

        // 判断出 是 admin / user
//        boolean isAdmin = scope.equals("admin");

        boolean isAdmin = "emp".equals(scope);   // 判断 "emp"

        String secretKey =
                isAdmin ? jwtProperties.getAdminSecretKey() : jwtProperties.getUserSecretKey();

        //1. 解析 refreshToken
        Claims claims;
        try{
            claims = JwtUtil.parseJWT(secretKey, refreshToken);

        }catch (Exception e) {
            throw new TokenInvalidException("refreshToken 无效");
        }
        //2.校验 token的类型是 access还是refresh
        Object typeObj = claims.get("type");
        if (typeObj == null || !"refresh".equals(typeObj.toString())) {
            throw new TokenInvalidException("不是有效的 refreshToken");
        }
        //3. 取出 userId

        String idKey =
                isAdmin ? JwtClaimsConstant.EMP_ID : JwtClaimsConstant.USER_ID;
        Object idObj = claims.get(idKey);
        if (idObj == null) {
            throw new TokenInvalidException("refreshToken 中缺少用户ID");
        }
        Long userId = Long.valueOf(idObj.toString());

        //如果是被禁用的员工，则要停发token
        if (isAdmin) {
            Employee employee = employeeMapper.getEmployeeById(userId);
            // 注意：必须先判空！员工可能被删除了
            if (employee == null || StatusConstant.DISABLE.equals(employee.getStatus())) {
                throw new TokenInvalidException("账号已删除或被禁用，禁止刷新令牌");
            }
        }

        // ============ 4. 加锁：串行化整个"校验+删除+签发"流程 ============
        String lockKey = "lock:refresh:" + scope + ":" + userId;
        RLock rLock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try{

            acquired = rLock.tryLock(3,10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new TokenInvalidException("请求过于频繁，请稍后再试");
            }

            //  关键：在锁内重新校验 Redis 里的 token 是否还是传入的这个
            //   如果两个请求同时进来，第一个请求会删除旧 token，
            //   第二个请求进来时 isValid 返回 false，被拦截
            if (!refreshTokenService.isValid(scope, userId, refreshToken)) {
                throw new TokenInvalidException("refreshToken 已失效（可能已使用过或被登出）");
            }


            // 删除旧 token（原子操作：删除成功才继续）
            refreshTokenService.delete(scope, userId);

            // 签发新 token
            String newAccessToken = createAccessToken(scope, userId);
            String newRefreshToken = createRefreshToken(scope, userId);  // 保存新 token
            long refreshTtl = isAdmin ? jwtProperties.getAdminRefreshTtl() : jwtProperties.getUserRefreshTtl();
            refreshTokenService.save(scope, newRefreshToken, userId, refreshTtl);

            log.info("【刷新令牌】成功：scope={}, userId={}", scope, userId);

            return Result.success(TokenPairVO.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build());



        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenInvalidException("请求被中断，请重试");
        } finally {
            if (acquired && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }

//        //4. 进行 redis的校验 校验redis里面存的 是不是这个key
//
//        if (!refreshTokenService.isValid(scope, userId, refreshToken)) {
//            throw new TokenInvalidException("refreshToken 已失效（可能已使用过或被登出）");
//        }
//
//        // ==========缺陷3修复：轮换模式：使用完立刻删除旧refreshToken，防止重放攻击 ==========
//        refreshTokenService.delete(scope, userId);
//
//        // 5. 签发新 accessToken + 新 refreshToken
//        String newAccessToken = createAccessToken(scope, userId);
//        String newRefreshToken = createRefreshToken(scope, userId);
//
//
//
//        // 6. 保存新 refreshToken（轮换）
//        long refreshTtl =
//                isAdmin ? jwtProperties.getAdminRefreshTtl() : jwtProperties.getUserRefreshTtl();
//
//        refreshTokenService.save(scope,  newRefreshToken,userId, refreshTtl);
//
//        return Result.success(TokenPairVO.builder()
//                .accessToken(newAccessToken)
//                .refreshToken(newRefreshToken)
//                .build());

    }

    /**
     * 创建新的 accessToken
     */
    public String createAccessToken(String scope, Long userId) {

        Map<String, Object> claims = new HashMap<>();


        if("emp".equals(scope)) {
            claims.put(JwtClaimsConstant.EMP_ID, userId);
            claims.put(JwtClaimsConstant.USER_TYPE,JwtClaimsConstant.TYPE_ADMIN);
            Employee employee = employeeMapper.getEmployeeById(userId);
            if(employee == null){
                throw new TokenInvalidException("该账号已被禁用或删除");
            }
            claims.put(JwtClaimsConstant.ROLE,employee.getRoleKey());

            return JwtUtil.createJWT(jwtProperties.getAdminSecretKey(),
                    jwtProperties.getAdminTtl(),claims );


        }
        // 对于user 也是 一样的
        else {
            claims.put(JwtClaimsConstant.USER_ID, userId);
            claims.put(JwtClaimsConstant.USER_TYPE, JwtClaimsConstant.TYPE_USER);
            return JwtUtil.createJWT(jwtProperties.getUserSecretKey(),
                    jwtProperties.getUserTtl(), claims);
        }


    }

    /**
     * 创建新的 refreshToken
     */
    public String createRefreshToken(String scope,Long userId){

        Map<String, Object> claims = new HashMap<>();
        if ("emp".equals(scope)) {
            claims.put(JwtClaimsConstant.EMP_ID, userId);
            claims.put("type", "refresh");
            return JwtUtil.createJWT(jwtProperties.getAdminSecretKey(),
                    jwtProperties.getAdminRefreshTtl(), claims);
        } else {
            claims.put(JwtClaimsConstant.USER_ID, userId);
            claims.put("type", "refresh");
            return JwtUtil.createJWT(jwtProperties.getUserSecretKey(),
                    jwtProperties.getUserRefreshTtl(), claims);
        }
    }



}
