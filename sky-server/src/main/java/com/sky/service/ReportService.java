package com.sky.service;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 统计指定时间区间内的营业额数据
     * @param beginDate
     * @param endDate
     * @return
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate beginDate, LocalDate endDate);



    /**
     * 统计指定时间区间内的营业额数据
     * @param beginDate
     * @param endDate
     * @return
     */
    UserReportVO getUserStatistics(LocalDate beginDate, LocalDate endDate);


    /**
     * 统计指定时间区间内的订单数
     * @param startDate
     * @param endDate
     * @return
     */
    OrderReportVO getOrderStatistics(LocalDate startDate, LocalDate endDate);

    /**
     * 拿到销量前10
     * @param startDate
     * @param endDate
     * @return
     */
    SalesTop10ReportVO getSalesTop10(LocalDate startDate, LocalDate endDate);


    /**
     *
     * 导出运营数据报表
     * @param response
     */
    void exportBusinessData(HttpServletResponse response);
}
