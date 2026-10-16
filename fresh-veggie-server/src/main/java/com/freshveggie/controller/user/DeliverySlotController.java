package com.freshveggie.controller.user;

import com.freshveggie.result.Result;
import com.freshveggie.service.DeliverySlotService;
import com.freshveggie.vo.DeliverySlotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userDeliverySlotController")
@Slf4j
@Tag(name = "配送时段接口")
@RequestMapping("/user/delivery-slot")
@RequiredArgsConstructor
public class DeliverySlotController {

    private final DeliverySlotService deliverySlotService;

    @GetMapping("/list")
    @Operation(description = "查询可预约的配送时段")
    public Result<List<DeliverySlotVO>> list() {
        log.info("用户端查询可预约配送时段");
        return Result.success(deliverySlotService.listEnabledSlots());
    }
}
