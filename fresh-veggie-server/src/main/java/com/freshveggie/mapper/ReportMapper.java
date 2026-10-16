package com.freshveggie.mapper;

import com.freshveggie.service.impl.ReportServiceImpl;
import com.freshveggie.vo.BusinessDataVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表统计 Mapper。
 *
 * <p>这里的方法**全部**是 MyBatis-Plus Wrapper 表达不了的 SQL：
 * <ul>
 *   <li>{@code WITH RECURSIVE} 递归日期序列（补全没有数据的日期）</li>
 *   <li>多表 join + group by + 聚合函数</li>
 *   <li>派生表与相关子查询</li>
 * </ul>
 *
 * <p>所以集中在这一个 XML 里，不要混进 OrderMapper / UserMapper 的 CRUD 查询中——
 * 这样「哪些 SQL 必须手写」一眼可见。
 *
 * <p>注意：本接口不继承 BaseMapper，因为它不对应单一实体，也没有通用 CRUD 需求。
 */
@Mapper
public interface ReportMapper {

    // 每日营业额(按支付时间,仅已完成订单,CTE 递归日期序列)
    List<ReportServiceImpl.TurnoverDate> getTurnoverList(@Param("begin") LocalDate begin,
                                                         @Param("end") LocalDate end);

    // 每日订单统计(新增 + 有效订单数)
    List<ReportServiceImpl.OrderDate> getOrdersStatistics(@Param("begin") LocalDate begin,
                                                          @Param("end") LocalDate end);

    // 商品销量 Top10(三表联表聚合)
    List<ReportServiceImpl.SalesTop10> getSaleProductTop10(@Param("begin") LocalDate begin,
                                                           @Param("end") LocalDate end);

    // 每日新增/累计用户统计(CTE 递归日期序列)
    List<ReportServiceImpl.UserDate> getUserStatistics(@Param("begin") LocalDate begin,
                                                       @Param("end") LocalDate end);

    // 营业额汇总(含完成率/客单价/新增用户)。begin/end 传 null 时按「今天」统计,
    // 被 WorkspaceController 的 dashboard 复用,勿删。
    BusinessDataVO getBusinessData(@Param("begin") LocalDate begin, @Param("end") LocalDate end);
}
