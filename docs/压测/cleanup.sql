-- ============================================================
-- 压测数据清理（压完务必执行，避免污染项目数据）
--   mysql -uroot -p sky_take_out < cleanup.sql
-- 只删"虚拟用户 9001+ 产生的东西"和"LOADTEST- 前缀的测试商品"
-- ============================================================

-- 订单主链路压测产生
DELETE FROM order_detail WHERE order_id IN (SELECT id FROM orders WHERE user_id >= 9001);
DELETE FROM orders        WHERE user_id >= 9001;
DELETE FROM shopping_cart WHERE user_id >= 9001;

-- 秒杀压测产生
DELETE FROM seckill_order WHERE user_id >= 9001;

-- 虚拟用户的地址簿
DELETE FROM address_book WHERE user_id >= 9001;

-- 测试商品
DELETE FROM dish          WHERE name = 'LOADTEST-DISH';
DELETE FROM seckill_goods WHERE name = 'LOADTEST-SECKILL';

-- 秒杀对账日志（压测期间可能产生大量记录，按需清理）
-- DELETE FROM seckill_reconcile_log WHERE goods_id IN (SELECT id FROM seckill_goods WHERE name = 'LOADTEST-SECKILL');

-- 手工测试留下的 5 张已取消订单（可选）
-- DELETE FROM order_detail WHERE order_id BETWEEN 46 AND 50;
-- DELETE FROM orders       WHERE id BETWEEN 46 AND 50;

SELECT
  (SELECT COUNT(*) FROM orders WHERE user_id >= 9001)        AS remaining_orders,
  (SELECT COUNT(*) FROM seckill_order WHERE user_id >= 9001) AS remaining_seckill_orders,
  (SELECT COUNT(*) FROM address_book WHERE user_id >= 9001)  AS remaining_addr;
