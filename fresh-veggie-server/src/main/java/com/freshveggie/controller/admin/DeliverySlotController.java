package com.freshveggie.controller.admin;

import com.freshveggie.dto.DeliverySlotDTO;
import com.freshveggie.entity.DeliverySlot;
import com.freshveggie.result.Result;
import com.freshveggie.service.DeliverySlotService;
import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Tag(name = "配送时段管理接口")
@RequestMapping("/admin/delivery-slot")
@Validated
@RequiredArgsConstructor
public class DeliverySlotController {

    private final DeliverySlotService deliverySlotService;

    @GetMapping
    @Operation(description = "查询时段列表(可按状态筛选)")
    public Result<List<DeliverySlot>> list(@RequestParam(required = false) Integer status) {
        log.info("查询配送时段，status：{}", status);
        return Result.success(deliverySlotService.listSlots(status));
    }

    @PostMapping
    @Operation(description = "新增配送时段")
    public Result<?> save(@RequestBody @Validated DeliverySlotDTO dto) {
        log.info("新增配送时段：{}", dto);
        // saveSlot 成功返回【新主键 id】(>0)，失败返回 0L。
        // 注意别写成 `== 0 ? success : error` —— 那样成功反而报"新增失败"，
        // 而数据其实已经插进去了（2026-10-01 修正）。
        return deliverySlotService.saveSlot(dto) > 0 ? Result.success() : Result.error("新增失败");
    }

    @PutMapping
    @Operation(description = "修改配送时段")
    public Result<?> update(@RequestBody @Validated(Update.class) DeliverySlotDTO dto) {
        log.info("修改配送时段：{}", dto);
        // updateSlot 失败会抛 BusinessException；能返回即成功。
        // 返回体带上更新后的实体，便于前端直接刷新列表项。
        return Result.success(deliverySlotService.updateSlot(dto));
    }

    @DeleteMapping
    @Operation(description = "删除配送时段")
    public Result<?> delete(@RequestParam @io.swagger.v3.oas.annotations.Parameter(description = "时段ID", required = true) Long id) {
        log.info("删除配送时段：{}", id);
        return deliverySlotService.deleteSlot(id) ? Result.success() : Result.error("删除失败");
    }

    @PutMapping("/status/{status}")
    @Operation(description = "配送时段启停用")
    public Result<?> changeStatus(
            @PathVariable @Range(max = 1L, message = "状态不合法") Integer status,
            @RequestParam @io.swagger.v3.oas.annotations.Parameter(description = "时段ID", required = true) Long id) {
        log.info("修改配送时段状态：id={}, status={}", id, status);
        DeliverySlot updated = deliverySlotService.changeSlotStatus(id, status);
        return updated == null ? Result.error("操作失败") : Result.success(updated);
    }
}
