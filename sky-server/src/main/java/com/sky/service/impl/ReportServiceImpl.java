package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import com.vueUser.mapper.OrderMapper;
import com.vueUser.mapper.UserMapper;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计 --- 查订单表（订单已经完成的）
     * @param begin
     * @param end
     * @return
     */

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {

        //当前集合 存放 从begin --- end 的范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();

        //日期计算  并且 加进到List中
        for(LocalDate date = begin ;
            date.isBefore(end.plusDays(1)) ;
            date = date.plusDays(1) ) {

            dateList.add(date);
        }

        //集合元素用 ,  分割
        String dls = StringUtils.join(dateList, ",");
        //传参
        TurnoverReportVO turnoverReportVO = new TurnoverReportVO();
        turnoverReportVO.setDateList(dls);


        //查询date日期对应的营业额数据 ，
        // 营业额是指 --> 状态为“已完成”的订单的金额统计
        List<Double> turnoverlist = new ArrayList<>();

        for (LocalDate date : dateList) {

            //select sum(amount) from orders where order_time < date_end（晚上23:59:59） and
            //    order_time > date_start（凌晨24：00:00） and status = 5 (5->代表已完成)

            LocalDateTime dayBegin = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);


            Map map = new HashMap();
            map.put("begin", dayBegin);
            map.put("end", dayEnd);
            map.put("status", Orders.COMPLETED);

            Double turnover = orderMapper.sumByMap(map);

            turnover = turnover == null ? 0.0 : turnover;


            turnoverlist.add(turnover);
        }

        String turnoverlistString = StringUtils.join(turnoverlist, ",");

        turnoverReportVO.setTurnoverList(turnoverlistString);


        return turnoverReportVO;


    }


    /**
     * 用户统计
     * @param beginDate
     * @param endDate
     * @return
     */
    @Override

    public UserReportVO getUserStatistics(LocalDate beginDate, LocalDate endDate) {

        //当前集合 存放 从begin --- end 的范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();

        //日期计算  并且 加进到List中
        for(LocalDate date = beginDate ;
            date.isBefore(endDate.plusDays(1)) ;
            date = date.plusDays(1) ) {

            dateList.add(date);
        }

        //集合元素用 ,  分割
        String dls = StringUtils.join(dateList, ",");

        //新用户数量
        //select count(id) from user where create_time < ???
        //    and create_time > ??? (每天的日期)
        List<Integer> newUserList = new ArrayList<>();

        //总的用户数量
        //select count(id) from user where create_time < ??? (在某天之前的全部注册就行)
        List<Integer> totalUserList = new ArrayList<>();

        //根据User的注册日期来进行统计用户数量

        for(LocalDate date : dateList) {

            LocalDateTime dayBegin = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("end", dayEnd);

            //总的数量
            Integer totalUserCount = userMapper.getUserCount(map);

            //新增
            map.put("begin", dayBegin);
            Integer newUserCount = userMapper.getUserCount(map);

            log.info("用户总数totalUserCount:" + totalUserCount);

            totalUserList.add(totalUserCount);
            newUserList.add(newUserCount);

        }


        return UserReportVO.builder().dateList(dls)
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();

    }


    /**
     * 统计指定时间区间内的订单数据
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate startDate, LocalDate endDate) {

        //当前集合 存放 从begin --- end 的范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();

        //日期计算  并且 加进到List中
        for(LocalDate date = startDate ;
            date.isBefore(endDate.plusDays(1)) ;
            date = date.plusDays(1) ) {

            dateList.add(date);
        }

        //集合元素用 ,  分割
        String dls = StringUtils.join(dateList, ",");

        //遍历datalist集合，查询每天的所有订单数和订单总数
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询每天订单总数
            /**
             * select count(id) from orders where order_time > 0:00 < and order_time <29:59
             */
            LocalDateTime dayBegin = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(dayBegin, dayEnd, null);
            //查询每天有效订单数
            /**
             * select count(id) from orders where order_time > 0:00 and order_time <29:59
             * and status = 5
             */
            Integer validOrderCount = getOrderCount(dayBegin, dayEnd, Orders.COMPLETED);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }


        //开始计算时间区间内的订单总数量

        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        //计算时间区间内的有效订单数量

        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();


        //计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount.doubleValue();
        }

        return   OrderReportVO.builder().dateList(dls)
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate).build();


    }

    /**
     * 统计指定时间区间内的销量排名前10
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(endDate, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesTop10 = orderMapper.getGoodsSalesTop10(start, end);

        //解决数据格式不匹配的问题
        //把list的每一个数据的 name 等属性 拼接 即可

        List<String> names =
                goodsSalesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String namesString = StringUtils.join(names, ",");

        List<Integer> numbers =
                goodsSalesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numbersString = StringUtils.join(numbers, ",");

        SalesTop10ReportVO build = SalesTop10ReportVO.builder()
                .nameList(namesString)
                .numberList(numbersString)
                .build();

        return build;

    }



    /**
     *
     * 导出运营数据报表
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {

        //1. 查询数据库 获取到营业数据 ---- 查询最近300天的数据

        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().plusDays(1);
        LocalDateTime beginDate = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endDate = LocalDateTime.of(end, LocalTime.MAX);

        BusinessDataVO businessDataVO = workspaceService.getBusinessData(beginDate, endDate);


        //2. 通过POI将数据写入到excel文件中
        InputStream resourceAsStream =
                this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        //基于模板文件来创建一个新的excel文件
        try {
            XSSFWorkbook excel = new XSSFWorkbook(resourceAsStream);
            //填充数据
            XSSFSheet sheet = excel.getSheetAt(0);  // 或 excel.getSheet("Sheet1")
            sheet.getRow(1).getCell(1).setCellValue("时间："+beginDate+"至"+endDate);
            //获得第4行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            //获得第5行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                //查询某一天的营业数据
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                //获得某一行
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //3. 通过输出流将Excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            //关闭资源
            out.close();
            excel.close();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }




    }

    /**
     *
     * 根据条件来统计订单数量
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end,Integer status) {
        HashMap map = new HashMap();

        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);
        return orderMapper.countByMap(map);
    }


}

























