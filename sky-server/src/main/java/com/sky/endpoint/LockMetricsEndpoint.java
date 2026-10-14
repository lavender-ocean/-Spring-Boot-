package com.sky.endpoint;

import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 锁指标 Actuator 端点
 * 访问：/actuator/lockMetrics
 */
@Slf4j
@Component
@Endpoint(id = "lockMetrics")
public class LockMetricsEndpoint {

    @Autowired
    private MeterRegistry meterRegistry;

    @ReadOperation
    public Map<String, Object> lockMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 锁获取耗时统计
        List<Map<String, Object>> lockTimers = new ArrayList<>();
        for (Meter meter : meterRegistry.getMeters()) {
            if (!"lock.acquire.duration".equals(meter.getId().getName())) {
                continue;
            }
            if (!(meter instanceof Timer)) continue;

            Timer timer = (Timer) meter;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tags", meter.getId().getTags().toString());
            item.put("count", timer.count());
            item.put("meanMs", round(timer.mean(TimeUnit.MILLISECONDS)));
            item.put("maxMs", round(timer.max(TimeUnit.MILLISECONDS)));

            // P50 / P95 / P99
            item.put("p50Ms", round(getPercentile(timer, 0.5)));
            item.put("p95Ms", round(getPercentile(timer, 0.95)));
            item.put("p99Ms", round(getPercentile(timer, 0.99)));

            lockTimers.add(item);
        }
        result.put("lockTimers", lockTimers);

        // 2. 锁失败次数
        Map<String, Double> failCounts = new HashMap<>();
        meterRegistry.getMeters().forEach(meter -> {
            if ("lock.acquire.failed.count".equals(meter.getId().getName())
                    && meter instanceof io.micrometer.core.instrument.Counter) {
                io.micrometer.core.instrument.Counter counter = (io.micrometer.core.instrument.Counter) meter;
                failCounts.put(meter.getId().getTags().toString(), counter.count());
            }
        });
        result.put("lockFailedCounts", failCounts);

        // 3. 缓存命中率
        Map<String, Double> cacheHits = new HashMap<>();
        meterRegistry.getMeters().forEach(meter -> {
            if ("cache.hit.count".equals(meter.getId().getName())
                    && meter instanceof io.micrometer.core.instrument.Counter) {
                io.micrometer.core.instrument.Counter counter = (io.micrometer.core.instrument.Counter) meter;
                cacheHits.put(meter.getId().getTags().toString(), counter.count());
            }
        });
        result.put("cacheHits", cacheHits);

        // 4. 缓存 Miss
        double cacheMiss = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if ("cache.miss.count".equals(meter.getId().getName())
                    && meter instanceof io.micrometer.core.instrument.Counter) {
                cacheMiss += ((io.micrometer.core.instrument.Counter) meter).count();
            }
        }
        result.put("cacheMissCount", cacheMiss);

        // 5. 计算 Caffeine 命中率
        double caffeineHit = 0;
        for (Map.Entry<String, Double> entry : cacheHits.entrySet()) {
            if (entry.getKey().contains("caffeine")) {
                caffeineHit += entry.getValue();
            }
        }
        double total = caffeineHit + cacheMiss;
        result.put("caffeineHitRate", total > 0 ? String.format("%.2f%%", caffeineHit / total * 100) : "0%");

        return result;
    }

    private double getPercentile(Timer timer, double percentile) {
        try {
            return timer.percentile(percentile, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return 0;
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}