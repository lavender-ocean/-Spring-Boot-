package com.sky.service;

import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.result.PageResult;


public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);


    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询方法
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用/禁止员工
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查信息
     * @param id
     * @return
     */
    Employee getEmployeeById(Long id);

    /**
     * 更新员工信息
     */
    void updateEmployee(EmployeeDTO employeeDTO);


    /**
     * 修改当前登陆人的密码
     * @param oldPassword
     * @param newPassword
     * @param userId
     */
    void editPassword(String oldPassword, String newPassword, Long userId);
}
