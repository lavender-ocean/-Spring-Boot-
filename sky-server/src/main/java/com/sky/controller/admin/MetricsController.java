package com.sky.controller.admin;

import com.sky.annotation.RequiresRole;
import com.sky.constant.RoleConstant;
import com.sky.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 可观测性指标接口（仅超管）
 */
@Slf4j
@RestController
@RequestMapping("/admin/metrics")
@Api(tags = "可观测性指标")
public class MetricsController {

    @Autowired
    private MeterRegistry meterRegistry;

    @GetMapping("/locks")
    @ApiOperation("查询锁指标")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<Map<String, Object>> lockMetrics() {
        Map<String, Object> data = new LinkedHashMap<>();

        List<Map<String, Object>> timers = new ArrayList<>();
        for (Meter meter : meterRegistry.getMeters()) {
            if (!"lock.acquire.duration".equals(meter.getId().getName())) continue;
            if (!(meter instanceof Timer)) continue;

            Timer t = (Timer) meter;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tags", meter.getId().getTags().toString());
            item.put("count", t.count());
            item.put("meanMs", round(t.mean(TimeUnit.MILLISECONDS)));
            item.put("maxMs", round(t.max(TimeUnit.MILLISECONDS)));
            item.put("p50Ms", round(t.percentile(0.5, TimeUnit.MILLISECONDS)));
            item.put("p95Ms", round(t.percentile(0.95, TimeUnit.MILLISECONDS)));
            item.put("p99Ms", round(t.percentile(0.99, TimeUnit.MILLISECONDS)));
            timers.add(item);
        }
        data.put("lockTimers", timers);

        Map<String, Double> failCounts = new LinkedHashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            if ("lock.acquire.failed.count".equals(meter.getId().getName())
                    && meter instanceof Counter) {
                failCounts.put(meter.getId().getTags().toString(),
                        ((Counter) meter).count());
            }
        }
        data.put("lockFailedCounts", failCounts);

        return Result.success(data);
    }

    @GetMapping("/cache")
    @ApiOperation("查询缓存指标")
    @RequiresRole(RoleConstant.SUPER_ADMIN)
    public Result<Map<String, Object>> cacheMetrics() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Double> hits = new LinkedHashMap<>();
        double miss = 0;
        for (Meter meter : meterRegistry.getMeters()) {
            if ("cache.hit.count".equals(meter.getId().getName())
                    && meter instanceof Counter) {
                hits.put(meter.getId().getTags().toString(), ((Counter) meter).count());
            }
            if ("cache.miss.count".equals(meter.getId().getName())
                    && meter instanceof Counter) {
                miss += ((Counter) meter).count();
            }
        }
        data.put("hits", hits);
        data.put("missCount", miss);

        double total = hits.values().stream().mapToDouble(Double::doubleValue).sum() + miss;
        data.put("hitRate", total > 0
                ? String.format("%.2f%%", hits.values().stream().mapToDouble(Double::doubleValue).sum() / total * 100)
                : "0%");

        return Result.success(data);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}