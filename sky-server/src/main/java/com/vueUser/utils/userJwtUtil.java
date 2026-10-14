package com.vueUser.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class userJwtUtil {

    /**
     * 生成用户token
     * @param secret 密钥
     * @param ttl 过期时间
     * @param id 用户id
     * @return
     */

    public String createUserJwt(String secret,long ttl,long id){

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        Map<String,Object> claims = new HashMap<>();

        claims.put("userId",id);
        //标记这是用户端token，区分管理员token
        claims.put("type","user");

        long now = System.currentTimeMillis();

        Date expireDate = new Date(now + ttl);

        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(expireDate)
                .signWith(key)
                .compact();



    }

    /**
     * 解析用户token
     */

    public static Claims parseJwt(String secretKey,String token){
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }




}
