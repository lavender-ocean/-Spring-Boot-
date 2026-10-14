package com.sky.controller.admin;

import com.sky.annotation.RequiresRole;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.RoleConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.service.token.JwtBlacklistService;
import com.sky.service.token.RefreshTokenService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    //加入 黑名单 功能
    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    //加入 双令牌 功能
    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "员工登陆")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        //login目前只做了个校验（账号密码）
        Employee employee = employeeService.login(employeeLoginDTO);


        /**

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        //设置 userId
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        //设置 userType
        claims.put(JwtClaimsConstant.USER_TYPE,JwtClaimsConstant.TYPE_ADMIN);

        //设置 role ---- 员工身份

        claims.put(JwtClaimsConstant.ROLE, employee.getRoleKey());   //  加这行




        log.info("-----这个员工的数据是Controller-----------------------------：{}", employee);




        //生成 token
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

         *
         */

        // 以下 使用 双令牌 机制

        //生成 accessToken

        //先生成 accessClaims
        Map<String,Object> accessClaims = new HashMap<>();
        accessClaims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        accessClaims.put(JwtClaimsConstant.USER_TYPE,JwtClaimsConstant.TYPE_ADMIN);
        accessClaims.put(JwtClaimsConstant.ROLE,employee.getRoleKey());

        //再生成 accessToken
        String accessToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey()
                ,jwtProperties.getAdminTtl()
                , accessClaims
        );

        //接下来 开始生成 refreshToken

        //refreshToken（payload 只有 empId，无 userType/role）

        //先生成 refreshClaims
        Map<String,Object> refreshClaims = new HashMap<>();
        refreshClaims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        refreshClaims.put("type", "refresh");       // 标识这是 refreshToken

        //再生成 refreshToken
        String refreshToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminRefreshTtl(), // 7 天
                refreshClaims
        );


        //accessToken封装给VO，返回发送给前端
        // 保存 refreshToken 到 Redis
        refreshTokenService.save("emp",  refreshToken, employee.getId(),jwtProperties.getAdminRefreshTtl());




        //封装给 VO
        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();


        log.info("开始登陆-=-=-=-=-=-=-=-=-=-=-=--=-=-=-=-=-=-=-=-=-=--=-=-=-=-=-=-=-=-=-=-=-=");

        log.info("userSecretKey = {}", jwtProperties.getUserSecretKey());
        log.info("userTokenName = {}", jwtProperties.getUserTokenName());

        log.info("登陆成功");

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation("员工退出")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {

        try {
            //1. 从请求头 拿出 token
            String token = request.getHeader(jwtProperties.getAdminTokenName());

            if (token == null || token.isEmpty()) {
                return Result.success();
            }

            //2.解析token ， 拿到 过期时间
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Date expireDate = claims.getExpiration();

            //3.加入 Redis 黑名单
            jwtBlacklistService.addBlackList(token, expireDate);


            // 同时清 Redis 里的 refreshToken
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            refreshTokenService.delete("emp", empId);

            log.info("员工登出成功，token 已拉黑");




        }catch (Exception e) {
            log.warn("登出时解析 token 失败：{}", e.getMessage());
            // 就算解析失败也不报错（可能已经过期）
        }

        return Result.success();

    }

    /**
     *
     * 注册
     */
    @ApiOperation("员工注册")
    @PostMapping
    public Result save(@RequestBody EmployeeDTO employeeDTO) {

        log.info("员工注册{}", employeeDTO);

        log.info("当前线程的id:{}", Thread.currentThread().getId());

        employeeService.save(employeeDTO);

        return Result.success();
    }


    /**
     *
     *
     */
    @ApiOperation("员工分页查询")
    @GetMapping("/page")
    public Result<PageResult> pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {

        PageResult pr = employeeService.pageQuery(employeePageQueryDTO);

        return Result.success(pr);
    }

    /**
     * 启用+禁止
     */


    @RequiresRole(RoleConstant.SUPER_ADMIN)
    @ApiOperation("启用禁止员工")
    @PostMapping("/status/{status}")
    public Result startOrStop(@PathVariable Integer status,Long id) {

        log.info("启用禁止员工账号{},{}", status, id);

        employeeService.startOrStop(status,id);

        return Result.success();
    }

    /**
     *
     * 根据ID查信息
     */

    @ApiOperation("根据ID查找员工信息")
    @GetMapping("/{id}")
    public Result<Employee> getEmployeeById(@PathVariable Long id) {

        Employee employee = employeeService.getEmployeeById(id);

        return Result.success(employee);
    }


    @RequiresRole(RoleConstant.SUPER_ADMIN)
    @ApiOperation("编辑员工信息")
    @PutMapping
    public Result update(@RequestBody EmployeeDTO employeeDTO) {

        employeeService.updateEmployee(employeeDTO);

        return Result.success();
    }

    @RequiresRole(RoleConstant.SUPER_ADMIN)
    @ApiOperation("修改当前登陆的人的密码")
    @PutMapping("/editPassword")
    public Result editPassword(@RequestBody PasswordDTO passwordDTO)  {

        String oldPassword = passwordDTO.getOldPassword();
        String newPassword = passwordDTO.getNewPassword();

        Long userId = BaseContext.getCurrentId();

        employeeService.editPassword(oldPassword, newPassword, userId);
        return Result.success();
    }


}