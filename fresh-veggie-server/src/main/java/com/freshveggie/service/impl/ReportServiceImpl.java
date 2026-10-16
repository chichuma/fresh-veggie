package com.freshveggie.service.impl;

import com.freshveggie.exception.BusinessException;
import com.freshveggie.mapper.ReportMapper;
import com.freshveggie.service.ReportService;
import com.freshveggie.vo.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        DateRange dateRange = checkDate(begin, end);
        begin = dateRange.begin();
        end = dateRange.end();

        List<TurnoverDate> turnoverDates = reportMapper.getTurnoverList(begin, end);
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner turnoverList = new StringJoiner(",");
        for (TurnoverDate turnover : turnoverDates) {
            dateList.add(turnover.date.toString());
            turnoverList.add(turnover.totalAmount.toString());
        }
        return TurnoverReportVO.builder()
                .dateList(dateList.toString())
                .turnoverList(turnoverList.toString())
                .build();
    }

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        DateRange dateRange = checkDate(begin, end);
        begin = dateRange.begin();
        end = dateRange.end();

        StringJoiner dateList = new StringJoiner(",");
        StringJoiner totalUserList = new StringJoiner(",");
        StringJoiner newUserList = new StringJoiner(",");
        List<UserDate> userDateList = reportMapper.getUserStatistics(begin, end);
        for (UserDate userDate : userDateList) {
            dateList.add(userDate.date.toString());
            newUserList.add(userDate.dailyNewUsers.toString());
            totalUserList.add(userDate.cumulativeUsers.toString());
        }
        return UserReportVO.builder()
                .dateList(dateList.toString())
                .totalUserList(totalUserList.toString())
                .newUserList(newUserList.toString())
                .build();
    }

    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        DateRange dateRange = checkDate(begin, end);
        begin = dateRange.begin();
        end = dateRange.end();

        StringJoiner dateList = new StringJoiner(",");
        StringJoiner orderCountList = new StringJoiner(",");
        StringJoiner validOrderCountList = new StringJoiner(",");
        BigDecimal totalOrderCount = BigDecimal.ZERO;
        BigDecimal validOrderCount = BigDecimal.ZERO;

        List<OrderDate> orderDateList = reportMapper.getOrdersStatistics(begin, end);
        for (OrderDate orderDate : orderDateList) {
            totalOrderCount = totalOrderCount.add(new BigDecimal(orderDate.dailyNewOrders));
            validOrderCount = validOrderCount.add(new BigDecimal(orderDate.dailyEffectiveNewOrders));
            dateList.add(orderDate.date.toString());
            orderCountList.add(orderDate.dailyNewOrders.toString());
            validOrderCountList.add(orderDate.dailyEffectiveNewOrders.toString());
        }
        BigDecimal orderCompletionRate = BigDecimal.ZERO;
        if (totalOrderCount.compareTo(BigDecimal.ZERO) != 0) {
            orderCompletionRate = validOrderCount.divide(totalOrderCount, 2, RoundingMode.HALF_UP);
        }
        return OrderReportVO.builder()
                .dateList(dateList.toString())
                .totalOrderCount(totalOrderCount.intValue())
                .validOrderCount(validOrderCount.intValue())
                .orderCompletionRate(orderCompletionRate.doubleValue())
                .orderCountList(orderCountList.toString())
                .validOrderCountList(validOrderCountList.toString())
                .build();
    }

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        DateRange dateRange = checkDate(begin, end);
        begin = dateRange.begin();
        end = dateRange.end();

        StringJoiner nameList = new StringJoiner(",");
        StringJoiner numberList = new StringJoiner(",");
        List<SalesTop10> salesTop10List = reportMapper.getSaleProductTop10(begin, end);
        for (int i = 0; i < salesTop10List.size() && i < 10; i++) {
            SalesTop10 item = salesTop10List.get(i);
            nameList.add(item.name);
            numberList.add(item.count.toString());
        }
        return SalesTop10ReportVO.builder()
                .nameList(nameList.toString())
                .numberList(numberList.toString())
                .build();
    }

    private DateRange checkDate(LocalDate begin, LocalDate end) {
        if (begin == null && end == null) {
            throw new BusinessException("范围过大，请限定时间范围");
        }
        if (begin == null) {
            begin = end.minusDays(29);
        }
        if (end == null) {
            end = begin.plusDays(29);
        }
        if (begin.isAfter(end)) {
            throw new BusinessException("时间选择错误");
        }
        if (ChronoUnit.DAYS.between(begin, end) > 30) {
            throw new BusinessException("时间超出30天，暂不支持查询");
        }
        return new DateRange(begin, end);
    }

    record DateRange(LocalDate begin, LocalDate end) {
    }

    @Data
    public static class TurnoverDate {
        private LocalDate date;
        private Double totalAmount;
    }

    @Data
    public static class UserDate {
        private LocalDate date;
        private Integer dailyNewUsers;
        private Integer cumulativeUsers;
    }

    @Data
    public static class OrderDate {
        private LocalDate date;
        private Integer dailyNewOrders;
        private Integer dailyEffectiveNewOrders;
    }

    @Data
    public static class SalesTop10 {
        private String name;
        private Integer count;
    }
}
