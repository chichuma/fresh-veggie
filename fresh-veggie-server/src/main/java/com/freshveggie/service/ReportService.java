package com.freshveggie.service;

import com.freshveggie.vo.OrderReportVO;
import com.freshveggie.vo.SalesTop10ReportVO;
import com.freshveggie.vo.TurnoverReportVO;
import com.freshveggie.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end);

    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);
}
