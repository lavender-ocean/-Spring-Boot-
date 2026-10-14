package com.sky.config;

import com.sky.mapper.SeckillGoodsMapper;
import com.sky.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时预热秒杀库存到 Redis
 */
@Slf4j
@Component
public class SeckillInitRunner implements ApplicationRunner {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 预热 goodsId = 1 的库存（演示用，实际应从 DB 查所有进行中的活动）
            List<Long> longs = seckillGoodsMapper.listLiveSeckillGoodsIds();
            for (Long id : longs) {
                int stock = seckillService.warmUp(id);
                if (stock > 0) {
                    log.info("秒杀库存预热成功：goodsId=1, stock={}", stock);
                }
            }
        } catch (Exception e) {
            log.warn("秒杀预热失败（可能数据库还没数据）：{}", e.getMessage());
        }
    }
}