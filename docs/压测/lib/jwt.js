'use strict';
/**
 * 手写 HS256 JWT 签发器（零依赖）
 *
 * 为什么自己签而不是调登录接口：
 *   压测需要成百上千个"虚拟用户"，如果每个都走登录，既慢又会把登录限流打满。
 *   项目用的是标准 HS256 + 固定密钥，所以可以直接用密钥离线签发 accessToken。
 *
 * 密钥来源：sky-server/src/main/resources/application.yml > sky.jwt.*
 *   admin-secret-key: sky123456sky123456sky123456sky123456
 *   user-secret-key:  user123456user123456user123456user123456
 *
 * 载荷字段来源（与登录接口签发的一致）：
 *   admin: { empId, role, userType: 'EMP' }
 *   user : { userId, userType: 'USER' }
 *   exp  : 秒级时间戳
 */
const crypto = require('crypto');

const ADMIN_SECRET = 'sky123456sky123456sky123456sky123456';
const USER_SECRET = 'user123456user123456user123456user123456';

function b64url(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(String(input));
  return buf.toString('base64').replace(/=+$/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function sign(payload, secret) {
  const header = b64url(JSON.stringify({ alg: 'HS256' }));
  const body = b64url(JSON.stringify(payload));
  const data = header + '.' + body;
  const sig = crypto.createHmac('sha256', secret).update(data).digest();
  return data + '.' + b64url(sig);
}

/** 造一个 C 端用户 token（userTokenName = user_token） */
function userToken(userId, ttlSeconds = 7200) {
  return sign(
    { userId, userType: 'USER', exp: Math.floor(Date.now() / 1000) + ttlSeconds },
    USER_SECRET
  );
}

/** 造一个管理端 token（adminTokenName = token） */
function adminToken(empId, role = 'SUPER_ADMIN', ttlSeconds = 7200) {
  return sign(
    { empId, role, userType: 'EMP', exp: Math.floor(Date.now() / 1000) + ttlSeconds },
    ADMIN_SECRET
  );
}

module.exports = { userToken, adminToken, sign, ADMIN_SECRET, USER_SECRET };
