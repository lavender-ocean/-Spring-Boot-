

package com.sky.Websocket;

import com.sky.context.UserContext;
import com.sky.exception.TokenInvalidException;
import com.sky.service.token.JwtAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket服务（统一鉴权 + 定向推送 + 心跳保活）
 *
 * 关键设计：
 * 1. EMP（管理端）和 USER（C端）的连接**分开存储**，避免 userId 碰撞
 * 2. EMP 用 empSessionMap，USER 用 userSessionMap
 * 3. 推送时按类型选择：sendToEmp / sendToUser / sendToRole
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    // ============ 静态注入（因为 @ServerEndpoint 是多例）============
    private static JwtAuthService jwtAuthService;

    @Autowired
    public void setJwtAuthService(JwtAuthService service) {

        WebSocketServer.jwtAuthService = service;
    }

    // ============ 核心数据结构 ============
    // sid → Session
    private static final ConcurrentHashMap<String, Session> sessionMap
            = new ConcurrentHashMap<>();
    // sid → 最后活跃时间
    private static final ConcurrentHashMap<String, Long> lastActiveMap
            = new ConcurrentHashMap<>();
    // sid → 角色（EMP / USER）
    private static final ConcurrentHashMap<String, String> roleMap
            = new ConcurrentHashMap<>();

    //【关键修复1】EMP 和 USER 分开存储，避免 ID 碰撞
    // empId → 该员工的多个 sid
    private static final ConcurrentHashMap<Long, Set<String>> empSessionMap
            = new ConcurrentHashMap<>();
    // userId → 该用户的多个 sid
    private static final ConcurrentHashMap<Long, Set<String>> userSessionMap
            = new ConcurrentHashMap<>();

    private static final long HEARTBEAT_TIMEOUT_MS = 90 * 1000L;

    // ============ 连接管理 ============

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("尝试建立连接，sid = {}", sid);

        // 1. 从请求参数取 token
        String token = extractToken(session);
        if (token == null || token.isEmpty()) {
            log.warn("连接被拒绝：缺少 token，sid = {}", sid);
            closeWithReason(session, "缺少 token");
            return;
        }

        // 2. 统一鉴权：先 EMP，失败后降级 USER
        Long userId;
        String userType;

        try {
            // 尝试 EMP（管理员）
            UserContext ctx = jwtAuthService.parseAdminToken(token);
            userId = ctx.getUserId();
            userType = ctx.getUserType();   // "EMP"
            log.info("EMP 鉴权成功，userId = {}", userId);

        } catch (TokenInvalidException e1) {
            // 不关闭 session！直接尝试 USER
            log.info("EMP 鉴权失败（预期内），尝试 USER 类型：sid = {}", sid);

            try {
                UserContext ctx = jwtAuthService.parseUserToken(token);
                userId = ctx.getUserId();
                userType = ctx.getUserType();   // "USER"
                log.info("USER 鉴权成功，userId = {}", userId);

            } catch (TokenInvalidException e2) {
                log.warn("WebSocket 鉴权彻底失败（EMP/USER 均失败），sid = {}, error = {}",
                        sid, e2.getMessage());
                closeWithReason(session, "token 无效");
                return;
            }
        }

        // 3. 防御：重复 sid
        if (sessionMap.containsKey(sid)) {
            log.warn("重复 sid，拒绝新连接：sid = {}", sid);
            closeWithReason(session, "sid 已存在");
            return;
        }

        // 4. 保存连接 + 维护反向索引
        sessionMap.put(sid, session);
        lastActiveMap.put(sid, System.currentTimeMillis());
        roleMap.put(sid, userType);

        // 【关键修复2】按类型存入不同的 Map
        if ("EMP".equals(userType)) {
            empSessionMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sid);
        } else {
            userSessionMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sid);
        }

        log.info(" 客户端：{} 建立连接，userId = {}, userType = {}, 当前在线数：{}",
                sid, userId, userType, sessionMap.size());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid, Session session) {
        lastActiveMap.put(sid, System.currentTimeMillis());

        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
            } catch (Exception e) {
                log.error("回复 pong 失败，sid = {}", sid, e);
            }
            return;
        }
        log.info("收到来自客户端：{} 的信息：{}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开：{}", sid);
        cleanup(sid);
        log.info("当前在线数：{}", sessionMap.size());
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        log.error("WebSocket 出错，sid = {}", sid, error);
        cleanup(sid);
    }

    /**
     * 统一清理：四张表 + 两张反向索引
     */
    private void cleanup(String sid) {
        sessionMap.remove(sid);
        lastActiveMap.remove(sid);
        String removedType = roleMap.remove(sid);

        // 从 empSessionMap 清理
        empSessionMap.forEach((empId, sidSet) -> {
            sidSet.remove(sid);
            if (sidSet.isEmpty()) {
                empSessionMap.remove(empId);
            }
        });

        // 从 userSessionMap 清理
        userSessionMap.forEach((userId, sidSet) -> {
            sidSet.remove(sid);
            if (sidSet.isEmpty()) {
                userSessionMap.remove(userId);
            }
        });
    }

    // ============ 推送能力 ============

    /**
     * 群发
     */
    public void sendToAllClients(String message) {
        log.info("群发消息给 {} 个连接", sessionMap.size());
        sessionMap.forEach((sid, session) -> sendOne(sid, session, message));
    }

    /**
     * 按员工 ID 推送（管理端）
     */
    public void sendToEmp(Long empId, String message) {
        Set<String> sids = empSessionMap.get(empId);
        if (sids == null || sids.isEmpty()) {
            log.warn("员工 {} 当前无在线连接，消息丢弃", empId);
            return;
        }
        log.info("定向推送给员工 {}，共 {} 个连接", empId, sids.size());
        for (String sid : sids) {
            Session session = sessionMap.get(sid);
            if (session != null) {
                sendOne(sid, session, message);
            }
        }
    }

    /**
     * 按用户 ID 推送（C端）
     */
    public void sendToUser(Long userId, String message) {
        Set<String> sids = userSessionMap.get(userId);
        if (sids == null || sids.isEmpty()) {
            log.warn("用户 {} 当前无在线连接，消息丢弃", userId);
            return;
        }
        log.info("定向推送给用户 {}，共 {} 个连接", userId, sids.size());
        for (String sid : sids) {
            Session session = sessionMap.get(sid);
            if (session != null) {
                sendOne(sid, session, message);
            }
        }
    }

    /**
     * 按角色推送（EMP / USER）
     */
    public void sendToRole(String role, String message) {
        List<String> targetSids = roleMap.entrySet().stream()
                .filter(e -> role.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (targetSids.isEmpty()) {
            log.warn("角色 {} 当前无在线连接", role);
            return;
        }
        log.info("按角色 {} 推送，共 {} 个连接", role, targetSids.size());
        for (String sid : targetSids) {
            Session session = sessionMap.get(sid);
            if (session != null) {
                sendOne(sid, session, message);
            }
        }
    }

    /**
     * 给单设备发消息
     */
    private void sendOne(String sid, Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (Exception e) {
            log.error("推送失败，sid = {}", sid, e);
        }
    }

    // ============ 心跳 ============

    public void checkHeartbeat() {
        long now = System.currentTimeMillis();
        List<String> toClose = new ArrayList<>();
        lastActiveMap.forEach((sid, lastActive) -> {
            if (now - lastActive > HEARTBEAT_TIMEOUT_MS) {
                toClose.add(sid);
            }
        });
        for (String sid : toClose) {
            log.warn("清理超时连接：{}", sid);
            Session session = sessionMap.get(sid);
            if (session != null) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "心跳超时"));
                } catch (Exception ignored) {}
            }
            cleanup(sid);
        }
    }

    // ============ 状态查询 ============

    public int getOnlineCount() {
        return sessionMap.size();
    }

    public Set<String> getOnlineIds() {
        return sessionMap.keySet();
    }

    /**
     * 查询某员工的在线连接数
     */
    public int getEmpOnlineCount(Long empId) {
        Set<String> sids = empSessionMap.get(empId);
        return sids == null ? 0 : sids.size();
    }

    /**
     * 查询某用户的在线连接数
     */
    public int getUserOnlineCount(Long userId) {
        Set<String> sids = userSessionMap.get(userId);
        return sids == null ? 0 : sids.size();
    }

    // ============ 工具方法 ============

    private String extractToken(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokenList = params.get("token");
        return (tokenList != null && !tokenList.isEmpty()) ? tokenList.get(0) : null;
    }

    private void closeWithReason(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception e) {
            log.error("关闭连接失败", e);
        }
    }
}
















































//package com.sky.Websocket;
//
//import com.sky.context.UserContext;
//import com.sky.exception.TokenInvalidException;
//import com.sky.properties.JwtProperties;
//import com.sky.service.token.JwtAuthService;
//import com.sky.utils.JwtUtil;
//import io.jsonwebtoken.Claims;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import javax.websocket.*;
//import javax.websocket.server.PathParam;
//import javax.websocket.server.ServerEndpoint;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.stream.Collectors;
//
///**
// * WebSocket服务（带握手鉴权）
// */
//@Slf4j
//@Component
//@ServerEndpoint("/ws/{sid}")
//public class WebSocketServer {
//
//    // ⚠️ 关键：@ServerEndpoint 的类是单例还是多例？
//    // WebSocket 每个连接会创建一个新实例，Spring 无法通过 @Autowired 注入静态字段
//    // 所以要注入到一个静态字段，通过 setter 方法注入
//    private static JwtProperties jwtProperties;
//
////
////    @Autowired
////    public void setJwtProperties(JwtProperties props) {
////        WebSocketServer.jwtProperties = props;
////    }
//
//
//    //开始 统一鉴权
//    private static JwtAuthService jwtAuthService;
//
//
//    @Autowired
//    public void setJwtAuthService(JwtAuthService service) {
//        WebSocketServer.jwtAuthService = service;
//    }
//
//    // 会话表
//    private static final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
//    // 活跃时间表
//    private static final ConcurrentHashMap<String, Long> lastActiveMap = new ConcurrentHashMap<>();
//    // 身份表：sid → empId（便于后续按身份定向推送）
//    private static final ConcurrentHashMap<String, Long> userMap = new ConcurrentHashMap<>();
//
//    private static final long HEARTBEAT_TIMEOUT_MS = 90 * 1000L;
//
//    // empId → 该员工的多个 sid（一个员工可能多设备登录）
//    private static final ConcurrentHashMap<Long, Set<String>> userSessionMap = new ConcurrentHashMap<>();
//    // sid → 角色（EMP / USER，将来可扩展）
//    private static final ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
//    /**
//     * 连接建立 → 第一步做鉴权
//     */
//    @OnOpen
//    public void onOpen(Session session, @PathParam("sid") String sid) {
//        log.info("尝试建立连接，sid = {}, queryString = {}", sid, session.getQueryString());
//
//
//        //下面这个东西 Tomcat不认啊 -----
//        // 1. 从 queryString 里取 token
////        String token = getQueryParam(session.getQueryString(), "token");
////
////        if (token == null || token.isEmpty()) {
////            log.warn("连接被拒绝：缺少 token，sid = {}", sid);
////            closeWithReason(session, "缺少 token");
////            return;
////        }
////
//
//        // ✅ 新写法：从 RequestParameterMap 取
//        String token = null;
//        Map<String, List<String>> params = session.getRequestParameterMap();
//        List<String> tokenList = params.get("token");
//        if (tokenList != null && !tokenList.isEmpty()) {
//            token = tokenList.get(0);
//        }
//
//
//        // 2. 校验 token
//
//        log.info("++++++++++++++token+++++++++++++++ = {}", token);
//
//
//        /**
//         * 统一 鉴权
//         *
//         */
//
//        Long userId;
//        String userType;
//        try {
//            UserContext ctx = jwtAuthService.parseAdminToken(token);
//            userId = ctx.getUserId();
//            userType = ctx.getUserType();   // "EMP"
//        } catch (TokenInvalidException e) {
//            log.warn("WebSocket 鉴权失败，sid = {}, error = {}", sid, e.getMessage());
//            closeWithReason(session, "token 无效");
//            return;
//        }
//
//
//        // 防御：重复 sid
//        if (sessionMap.containsKey(sid)) {
//            log.warn("重复 sid，拒绝新连接：sid = {}", sid);
//            closeWithReason(session, "sid 已存在");
//            return;
//        }
//
//        // 4. 保存连接 + 维护反向索引
//        sessionMap.put(sid, session);
//        lastActiveMap.put(sid, System.currentTimeMillis());
//        roleMap.put(sid, userType);
//        userSessionMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sid);
//
//        log.info("✅ 客户端：{} 建立连接，userId = {}, userType = {}, 当前在线数：{}",
//                sid, userId, userType, sessionMap.size());
//
//
////        Long empId;
////        try {
////            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
////            empId = Long.valueOf(claims.get("empId").toString());
////        } catch (Exception e) {
////            log.warn("连接被拒绝：token 无效，sid = {}, error = {}", sid, e.getMessage());
////            closeWithReason(session, "token 无效");
////            return;
////        }
////
////        // 3. 鉴权通过 → 保存连接
////        sessionMap.put(sid, session);
////        lastActiveMap.put(sid, System.currentTimeMillis());
////        userMap.put(sid, empId);
////
////        log.info("✅ 客户端：{} 建立连接，员工ID = {}，当前在线数：{}", sid, empId, sessionMap.size());
//    }
//
//    /**
//     * 收到消息
//     */
//    @OnMessage
//    public void onMessage(String message, @PathParam("sid") String sid, Session session) {
//        lastActiveMap.put(sid, System.currentTimeMillis());
//
//        if ("ping".equals(message)) {
//            try {
//                session.getBasicRemote().sendText("pong");
//            } catch (Exception e) {
//                log.error("回复 pong 失败，sid = {}", sid, e);
//            }
//            return;
//        }
//
//        log.info("收到来自客户端：{} 的信息：{}", sid, message);
//    }
//
//    /**
//     * 连接关闭
//     */
//    @OnClose
//    public void onClose(@PathParam("sid") String sid) {
//        log.info("连接断开：{}", sid);
//        sessionMap.remove(sid);
//        lastActiveMap.remove(sid);
//        userMap.remove(sid);
//
//        cleanup(sid);
//
//        log.info("当前在线数：{}", sessionMap.size());
//    }
//
//    /**
//     * 连接出错
//     */
//    @OnError
//    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
//        log.error("WebSocket 出错，sid = {}", sid, error);
//        sessionMap.remove(sid);
//        lastActiveMap.remove(sid);
//        userMap.remove(sid);
//
//        cleanup(sid);
//    }
//
////    ================       推送 消息           =======================================
//
//
//    /**
//     * 群发
//     */
//    public void sendToAllClient(String message) {
//        Collection<Session> sessions = sessionMap.values();
//        for (Session session : sessions) {
//            try {
//                session.getBasicRemote().sendText(message);
//            } catch (Exception e) {
//                log.error("推送消息失败，sessionId = {}", session.getId(), e);
//            }
//        }
//    }
//
//    /**
//     * 按员工 ID 推送（一个员工多设备都会收到）
//     */
//    public void sendToUser(Long userId, String message) {
//        Set<String> sids = userSessionMap.get(userId);
//        if (sids == null || sids.isEmpty()) {
//            log.warn("员工 {} 当前无在线连接，消息丢弃", userId);
//            return;
//        }
//        log.info("定向推送给员工 {}，共 {} 个连接", userId, sids.size());
//        for (String sid : sids) {
//            Session session = sessionMap.get(sid);
//            if (session != null) {
//                sendOne(sid, session, message);
//            }
//        }
//    }
//
//
//    /**
//     * 按角色推送（如：EMP / USER）
//     */
//    public void sendToRole(String role, String message) {
//        List<String> targetSids = roleMap.entrySet().stream()
//                .filter(e -> role.equals(e.getValue()))
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        if (targetSids.isEmpty()) {
//            log.warn("角色 {} 当前无在线连接", role);
//            return;
//        }
//        log.info("按角色 {} 推送，共 {} 个连接", role, targetSids.size());
//        for (String sid : targetSids) {
//            Session session = sessionMap.get(sid);
//            if (session != null) {
//                sendOne(sid, session, message);
//            }
//        }
//    }
//
//
//    /**
//     * 给单设备 发信息
//     * @param sid
//     * @param session
//     * @param message
//     */
//    private void sendOne(String sid, Session session, String message) {
//        try {
//            session.getBasicRemote().sendText(message);
//        } catch (Exception e) {
//            log.error("推送失败，sid = {}", sid, e);
//        }
//    }
//
//
//    /**
//     * 心跳扫描
//     */
//    public void checkHeartbeat() {
//        long now = System.currentTimeMillis();
//        for (String sid : sessionMap.keySet()) {
//            Long lastActive = lastActiveMap.get(sid);
//            if (lastActive == null || now - lastActive > HEARTBEAT_TIMEOUT_MS) {
//                closeSession(sid, "心跳超时");
//            }
//
//
//
//            cleanup(sid);
//
//        }
//    }
//
//    public int getOnlineCount() {
//        return sessionMap.size();
//    }
//
//    public Set<String> getOnlineIds() {
//        return sessionMap.keySet();
//    }
//
//    // ============ 工具方法 ============
//
//    /**
//     * 从 queryString 里取参数值
//     * @param queryString "token=xxx&other=yyy"
//     * @param name 参数名
//     */
//    private String getQueryParam(String queryString, String name) {
//        if (queryString == null)
//            return null;
//        String prefix = name + "=";
//        for (String pair : queryString.split("&")) {
//            if (pair.startsWith(prefix)) {
//                return pair.substring(prefix.length());
//            }
//        }
//        return null;
//    }
//
//    /**
//     * 优雅关闭连接
//     */
//    private void closeWithReason(Session session, String reason) {
//        try {
//            session.close(new CloseReason(
//                    CloseReason.CloseCodes.VIOLATED_POLICY,   // 1008 策略违规
//                    reason
//            ));
//        } catch (Exception e) {
//            log.error("关闭连接失败", e);
//        }
//    }
//
//    private void closeSession(String sid, String reason) {
//        Session session = sessionMap.remove(sid);
//        lastActiveMap.remove(sid);
//        userMap.remove(sid);
//        if (session != null) {
//            try {
//                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
//            } catch (Exception e) {
//                log.error("关闭 session 失败，sid = {}", sid, e);
//            }
//        }
//        log.warn("清理连接：{}，原因：{}，当前在线数：{}", sid, reason, sessionMap.size());
//    }
//
//
//
//    /**
//     * ✅ 统一清理一个连接的所有索引（关键！）
//     * sessionMap / lastActiveMap / roleMap / userSessionMap 四张表都要清
//     */
//    private void cleanup(String sid) {
//        sessionMap.remove(sid);
//        lastActiveMap.remove(sid);
//        roleMap.remove(sid);
//
//        // 从 userSessionMap 里删除该 sid，如果该员工的所有连接都断了，就把员工 key 也删掉
//        userSessionMap.forEach((userId, sidSet) -> {
//            sidSet.remove(sid);
//            if (sidSet.isEmpty()) {
//                userSessionMap.remove(userId);
//            }
//        });
//    }
//}