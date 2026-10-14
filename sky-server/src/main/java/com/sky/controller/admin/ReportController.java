package com.sky.controller.admin;


import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计相关接口")
public class ReportController {

    @Autowired
    private ReportService reportService;


    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @RequestParam String start,
            @RequestParam String end
    ) {


        // 手动解析
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);


        log.info("营业额统计");



        log.info("startDate:{},endDate:{}", startDate, endDate);


        reportService.getTurnoverStatistics(startDate, endDate);



        return Result.success(reportService.getTurnoverStatistics(startDate, endDate));
    }



    @ApiOperation("用户统计")
    @GetMapping("/userStatistics")
    public Result<UserReportVO> userStatistics(
            @RequestParam String start,
            @RequestParam String end
    ){

        // 手动解析
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);


        log.info("用户数据统计");

        log.info("startDate:{},endDate:{}", startDate, endDate);

        UserReportVO userStatistics = reportService.getUserStatistics(startDate, endDate);

        return Result.success(userStatistics);
    }






    @ApiOperation("顶单统计")
    @GetMapping("/ordersStatistics")
    public Result<OrderReportVO> ordersStatistics(
            @RequestParam String start,
            @RequestParam String end
    ){

        // 手动解析
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        log.info("订单统计");

        log.info("startDate:{},endDate:{}", startDate, endDate);


        OrderReportVO orderReportVO = reportService.getOrderStatistics(startDate, endDate);

        return Result.success(orderReportVO);
    }




    @ApiOperation("销量排名")
    @GetMapping("/top10")
    public Result<SalesTop10ReportVO> top10(
            @RequestParam String start,
            @RequestParam String end
    ){

        // 手动解析
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);


        log.info("销量统计");

        log.info("startDate:{},endDate:{}", startDate, endDate);


        SalesTop10ReportVO salesTop10ReportVO = reportService.getSalesTop10(startDate, endDate);

        return Result.success(salesTop10ReportVO);
    }


    @GetMapping("/export")
    @ApiOperation("导出最近30天的数据")
    public void export(HttpServletResponse response){





        log.info("导出最近30天的数据");
        reportService.exportBusinessData(response);

    }







}
