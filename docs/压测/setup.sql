-- ============================================================
-- 压测测试数据准备（执行前先确认连的是 sky_take_out 库）
--   mysql -uroot -p sky_take_out < setup.sql
-- 说明：
--   * 虚拟用户 id 从 9001 起，和真实用户隔离，便于一键清理
--   * 秒杀商品库存固定 100，用于"防超卖"正确性断言（成功数必须 == 100）
--   * 下单场景需要一个库存很大的测试菜品，避免压到一半就"库存不足"
-- ============================================================

-- ① 下单压测专用菜品（库存 10 万，够打）
--    注意：字段值一律用 ASCII —— Windows 终端默认 GBK，中文写进 varchar 容易触发
--    "Data too long for column"，与压测无关的坑不要在这里踩
DELETE FROM dish WHERE name = 'LOADTEST-DISH';
INSERT INTO dish (name, category_id, price, image, description, status, stock,
                  create_time, update_time, create_user, update_user)
VALUES ('LOADTEST-DISH', 11, 1.00, '', 'loadtest', 1, 100000,
        NOW(), NOW(), 1, 1);
SELECT id AS loadtestDishId FROM dish WHERE name = 'LOADTEST-DISH';

-- ② 秒杀压测专用商品（库存 100，窗口覆盖当前时间）
DELETE FROM seckill_goods WHERE name = 'LOADTEST-SECKILL';
INSERT INTO seckill_goods (name, image, original_price, seckill_price, stock,
                           start_time, end_time, status, create_time, update_time)
VALUES ('LOADTEST-SECKILL', '', 100.00, 9.90, 100,
        NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 3 HOUR, 1, NOW(), NOW());
SELECT id AS loadtestGoodsId FROM seckill_goods WHERE name = 'LOADTEST-SECKILL';

-- ③ 虚拟用户的地址簿：id 与 user_id 相同（9001..9300）
--    这样压测脚本里 addressBookId 直接取 userId，省掉映射表
DELETE FROM address_book WHERE user_id >= 9001;
INSERT INTO address_book (id, user_id, consignee, sex, phone, detail, label, is_default)
WITH RECURSIVE seq AS (
    SELECT 9001 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 9300
)
SELECT n, n, 'lt', '0', '13800000000', 'loadtest', 'LT', 0 FROM seq;

SELECT COUNT(*) AS addressBooksForLoadTest FROM address_book WHERE user_id >= 9001;
