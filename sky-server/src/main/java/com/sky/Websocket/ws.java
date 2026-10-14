//package com.sky.Websocket;
//
//import com.sky.context.UserContext;
//import com.sky.exception.TokenInvalidException;
//import com.sky.service.token.JwtAuthService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import javax.websocket.*;
//import javax.websocket.server.PathParam;
//import javax.websocket.server.ServerEndpoint;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.stream.Collectors;
//
///**
// * WebSocket服务（统一鉴权 + 定向推送 + 心跳保活）
// */
//@Slf4j
//@Component
//@ServerEndpoint("/ws/{sid}")
//public class WebSocketServer {
//
//    // ============ 静态注入（因为 @ServerEndpoint 是多例）============
//    private static JwtAuthService jwtAuthService;
//
//    @Autowired
//    public void setJwtAuthService(JwtAuthService service) {
//        WebSocketServer.jwtAuthService = service;
//    }
//
//    // ============ 核心数据结构 ============
//    // sid → Session
//    private static final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
//    // sid → 最后活跃时间
//    private static final ConcurrentHashMap<String, Long> lastActiveMap = new ConcurrentHashMap<>();
//    // empId → 该员工的多个 sid（一个员工可能多设备登录）
//    private static final ConcurrentHashMap<Long, Set<String>> userSessionMap = new ConcurrentHashMap<>();
//    // sid → 角色（EMP / USER，将来可扩展）
//    private static final ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();
//
//    private static final long HEARTBEAT_TIMEOUT_MS = 90 * 1000L;
//
//    // ============ 连接管理 ============
//
//    @OnOpen
//    public void onOpen(Session session, @PathParam("sid") String sid) {
//        log.info("尝试建立连接，sid = {}", sid);
//
//        // 1. 从请求参数取 token（Tomcat 下 getQueryString 可能为 null，用这个更稳）
//        String token = extractToken(session);
//        if (token == null || token.isEmpty()) {
//            log.warn("连接被拒绝：缺少 token，sid = {}", sid);
//            closeWithReason(session, "缺少 token");
//            return;
//        }
//
//        // 2. 统一鉴权（复用 JwtAuthService）
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
//        // 3. 防御：重复 sid
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
//    }
//
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
//        log.info("收到来自客户端：{} 的信息：{}", sid, message);
//    }
//
//    @OnClose
//    public void onClose(@PathParam("sid") String sid) {
//        log.info("连接断开：{}", sid);
//        cleanup(sid);
//        log.info("当前在线数：{}", sessionMap.size());
//    }
//
//    @OnError
//    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
//        log.error("WebSocket 出错，sid = {}", sid, error);
//        cleanup(sid);
//    }
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
//
//    // ============ 推送能力 ============
//
//    /**
//     * 群发
//     */
//    public void sendToAllClients(String message) {
//        log.info("群发消息给 {} 个连接", sessionMap.size());
//        sessionMap.forEach((sid, session) -> sendOne(sid, session, message));
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
//    /**
//     * 给单设备发消息
//     */
//    private void sendOne(String sid, Session session, String message) {
//        try {
//            session.getBasicRemote().sendText(message);
//        } catch (Exception e) {
//            log.error("推送失败，sid = {}", sid, e);
//        }
//    }
//
//    // ============ 心跳 ============
//
//    public void checkHeartbeat() {
//        long now = System.currentTimeMillis();
//        List<String> toClose = new ArrayList<>();
//        lastActiveMap.forEach((sid, lastActive) -> {
//            if (now - lastActive > HEARTBEAT_TIMEOUT_MS) {
//                toClose.add(sid);
//            }
//        });
//        for (String sid : toClose) {
//            log.warn("清理超时连接：{}", sid);
//            Session session = sessionMap.get(sid);
//            if (session != null) {
//                try {
//                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "心跳超时"));
//                } catch (Exception ignored) {}
//            }
//            cleanup(sid);
//        }
//    }
//
//    // ============ 状态查询 ============
//
//    public int getOnlineCount() {
//        return sessionMap.size();
//    }
//
//    public Set<String> getOnlineIds() {
//        return sessionMap.keySet();
//    }
//
//    /**
//     * 查询某员工的在线连接数
//     */
//    public int getUserOnlineCount(Long userId) {
//        Set<String> sids = userSessionMap.get(userId);
//        return sids == null ? 0 : sids.size();
//    }
//
//    // ============ 工具方法 ============
//
//    /**
//     * 从请求参数里取 token（Tomcat 下 getQueryString 可能为 null）
//     */
//    private String extractToken(Session session) {
//        Map<String, List<String>> params = session.getRequestParameterMap();
//        List<String> tokenList = params.get("token");
//        return (tokenList != null && !tokenList.isEmpty()) ? tokenList.get(0) : null;
//    }
//
//    private void closeWithReason(Session session, String reason) {
//        try {
//            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
//        } catch (Exception e) {
//            log.error("关闭连接失败", e);
//        }
//    }
//}