'use strict';
/**
 * 压测用 HTTP 客户端
 *
 * 关键点：必须开 keep-alive 连接池
 *   - 否则每个请求新建连接，压测脚本自己会因 TIME_WAIT / 端口耗尽先崩，
 *     测出来的是"压测机的连接创建能力"，不是应用能力
 */
const http = require('http');

function makeAgent(concurrency) {
  const sockets = Math.max(concurrency, 10);
  return new http.Agent({
    keepAlive: true,
    maxSockets: sockets,
    maxFreeSockets: sockets,
    timeout: 60000,
  });
}

/**
 * 发一个请求
 * @returns {Promise<{status:number, json:object|null, raw:string}>}
 */
function request(agent, opts) {
  const {
    method = 'GET',
    url,
    token,
    tokenHeader = 'user_token',
    body,
    timeoutMs = 30000,
  } = opts;

  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const payload = body === undefined ? null : Buffer.from(JSON.stringify(body));
    const headers = {};
    if (token) headers[tokenHeader] = token;
    if (payload) {
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = payload.length;
    }

    const req = http.request(
      {
        agent,
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method,
        headers,
        timeout: timeoutMs,
      },
      (res) => {
        let raw = '';
        res.setEncoding('utf8');
        res.on('data', (c) => {
          raw += c;
        });
        res.on('end', () => {
          let json = null;
          try {
            json = JSON.parse(raw);
          } catch (e) {
            // 非 JSON 响应（例如 /test/mq/send 返回纯字符串），保留 raw 供排查
          }
          resolve({ status: res.statusCode, json, raw });
        });
      }
    );

    req.on('timeout', () => req.destroy(new Error('ETIMEOUT')));
    req.on('error', (e) => reject(e));
    if (payload) req.write(payload);
    req.end();
  });
}

module.exports = { makeAgent, request };
