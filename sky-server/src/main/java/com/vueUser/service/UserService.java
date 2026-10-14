package com.vueUser.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;

public interface UserService {


    /**
     * 用户进行 登陆
     * @param userLoginDTO
     * @return
     */
    User login(UserLoginDTO userLoginDTO) throws Exception;



}
