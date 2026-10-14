package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import com.sky.utils.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;


import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);


        log.info("-----这个员工的数据是-----------------------------：{}", employee);


        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        /**
         * 改进为 BCrypt 加密
         */
//        password = DigestUtils.md5DigestAsHex(password.getBytes());



        //现在就完成了
        //log.info(password);

//        if (!password.equals(employee.getPassword())) {
//            //密码错误
//            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
//        }

//        log.info("=====================================================================");
//        log.info("BCrypt密码：{}",PasswordUtil.encoderBCrypt(password));
//        log.info("=====================================================================");


        if (!PasswordUtil.matchesBCrypt(employeeLoginDTO.getPassword(), employee.getPassword())) {

            //密码错误

            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }


        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    @Override
    public void save(EmployeeDTO employeeDTO) {

        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employee.setStatus(StatusConstant.ENABLE);

        String password = PasswordUtil.encoderBCrypt(PasswordConstant.DEFAULT_PASSWORD);

        employee.setPassword(password);

        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());


        log.info("当前线程的id:{}", Thread.currentThread().getId());


        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);

    }

    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {

        PageHelper.startPage(employeePageQueryDTO.getPage(),employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total,records);
    }

    @Override
    public void startOrStop(Integer status, Long id) {

        Employee employee = new Employee();
        employee.setStatus(status);
        employee.setId(id);

        employeeMapper.updateAll(employee);

    }

    @Override
    public Employee getEmployeeById(Long id) {

        Employee employee = employeeMapper.getEmployeeById(id);

        return employee;

    }

    @Override
    public void updateEmployee(EmployeeDTO employeeDTO) {

        Employee employee = new Employee();

        BeanUtils.copyProperties(employeeDTO,employee );

        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.updateAll(employee);
    }

    /**
     * 修改当前登录人的密码
     * @param oldPassword
     * @param newPassword
     * @param userId
     */
    @Override
    public void editPassword(String oldPassword, String newPassword, Long userId) {

        Employee employee = employeeMapper.getEmployeeById(userId);

        /**
         * 修改为 BCrypt 加密
         */

//        employee.setPassword(DigestUtils.md5DigestAsHex(newPassword.getBytes()));

        String password = PasswordUtil.encoderBCrypt(newPassword);

        employee.setPassword(password);

        employeeMapper.updateAll(employee);

    }


}
