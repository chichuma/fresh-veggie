package com.freshveggie.controller.admin;

import com.freshveggie.constant.JwtClaimsConstant;
import com.freshveggie.dto.StaffDTO;
import com.freshveggie.dto.StaffLoginDTO;
import com.freshveggie.dto.pageQuery.StaffPageQueryDTO;
import com.freshveggie.entity.Staff;
import com.freshveggie.properties.JwtProperties;
import com.freshveggie.result.PageResult;
import com.freshveggie.result.Result;
import com.freshveggie.service.StaffService;
import com.freshveggie.utils.JwtUtil;
import com.freshveggie.valid.groups.Update;
import com.freshveggie.vo.StaffLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/staff")
@Slf4j
@Tag(name = "用户接口")
@Validated
@RequiredArgsConstructor
public class StaffController {

    private final StaffService employeeService;
    private final JwtProperties jwtProperties;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO 前端传入的用户名和密码
     * @return 包含员工信息和JWT令牌的登录响应
     */
    @PostMapping("/login")
    @Operation(description = "员工登录")
    public Result<StaffLoginVO> login(@RequestBody @Valid StaffLoginDTO employeeLoginDTO) {
        log.info("员工登录{}",employeeLoginDTO);

        Staff employee = employeeService.login(employeeLoginDTO);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );
        StaffLoginVO employeeLoginVO = StaffLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    @PostMapping("/logout")
    @Operation(description = "退出登录")
    public Result<String> logout() {
        return Result.success();
    }

    @PostMapping
    @Operation(description = "新增员工")
    public Result<?> save(@RequestBody @Valid StaffDTO employeeDTO) {
        log.info("新增员工{}",employeeDTO);
        return employeeService.save(employeeDTO) ? Result.success(null) : Result.error("添加失败");
    }

    @GetMapping("/page")
    @Operation(description = "获取员工列表")
    public Result<PageResult<Staff>> listStaff(StaffPageQueryDTO employeePageQueryDTO) {
        log.info("获取员工列表:{}",employeePageQueryDTO);
        PageResult<Staff> employeePageResult = employeeService.listStaff(employeePageQueryDTO);
        return Result.success(employeePageResult);
    }

    @PostMapping("/status/{status}")
    @Operation(description = "修改员工状态")
    public Result<?> changeStaffStatus(
            @Parameter(description = "员工状态",required = true)
            @PathVariable
            @Range(max = 1L, message = "status不合法")
            @NotNull(message = "status不能为空")
            Integer status,

            @RequestParam
            @NotNull(message = "员工ID不能为空")
            @Parameter(description = "员工ID", required = true)
            Long id
    ) {
        log.info("员工修改状态，id：{}，状态值：{}", id, status);
        employeeService.changeStaffStatus(status, id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(description = "根据ID查询员工信息")
    public Result<Staff> getStaffById(
            @PathVariable
            @Parameter(description = "员工ID", required = true)
            @NotNull(message = "员工ID不能为空")
            Long id
    ) {
        log.info("获取员工信息，id: {}", id);
        Staff employee = employeeService.getStaffById(id);
        return Result.success(employee);
    }


    @PutMapping
    @Operation(description = "更新员工信息")
    public Result<?> updateStaff(@RequestBody @Validated(Update.class) StaffDTO employeeDTO) {
        log.info("编辑员工信息：{}", employeeDTO);
        employeeService.updateStaff(employeeDTO);
        return Result.success();
    }
}
