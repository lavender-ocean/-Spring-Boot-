package com.vueUser.service.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.LoginFailedException;

import com.sky.exception.PasswordErrorException;
import com.sky.properties.WeChatProperties;

import com.sky.utils.HttpClientUtil;
import com.sky.utils.PasswordUtil;
import com.vueUser.mapper.UserMapper;
import com.vueUser.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    //微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

//    /**
//     * 微信登录
//     * @param userLoginDTO
//     * @return
//     */
//    public User wxLogin(UserLoginDTO userLoginDTO) {
//        String openid = getOpenid(userLoginDTO.getCode());
//
//        //判断openid是否为空，如果为空表示登录失败，抛出业务异常
//        if(openid == null){
//            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
//        }
//
//        //判断当前用户是否为新用户
//        User user = userMapper.getByOpenid(openid);
//
//        //如果是新用户，自动完成注册
//        if(user == null){
//            user = User.builder()
//                    .openid(openid)
//                    .createTime(LocalDateTime.now())
//                    .build();
//            userMapper.insert(user);
//        }
//
//        //返回这个用户对象
//        return user;
//    }

//    /**
//     * 调用微信接口服务，获取微信用户的openid
//     * @param code
//     * @return
//     */
//    private String getOpenid(String code){
//        //调用微信接口服务，获得当前微信用户的openid
//        Map<String, String> map = new HashMap<>();
//        map.put("appid",weChatProperties.getAppid());
//        map.put("secret",weChatProperties.getSecret());
//        map.put("js_code",code);
//        map.put("grant_type","authorization_code");
//        String json = HttpClientUtil.doGet(WX_LOGIN, map);
//
//        JSONObject jsonObject = JSON.parseObject(json);
//        String openid = jsonObject.getString("openid");
//        return openid;
//    }


    /**
     * vue 用户 进行登陆
     * @param userLoginDTO
     * @return
     */
    @Override
    public User login(UserLoginDTO userLoginDTO) throws Exception {

        String phone = userLoginDTO.getPhone();
        String password = userLoginDTO.getPassword();

        //1. 按手机号查找用户
        User user = userMapper.getByPhone(phone);

        // 第一步必须判null
        if(user == null){
            // 登录失败：用户不存在
            throw new Exception("用户不存在！！！！！！！！");
        }

        if( user.getPhone() == null ){
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //2. 校验密码 使用 BCrypt

        //先测试拿到密码
//        log.info("用户端密码是：{}",PasswordUtil.encoderBCrypt(password));

        if(!PasswordUtil.matchesBCrypt(password, user.getPassword())){
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);

        }
        return user;


    }
}
