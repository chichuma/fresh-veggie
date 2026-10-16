package com.freshveggie.controller.admin;

import com.freshveggie.annotation.validation.File;
import com.freshveggie.result.Result;
import com.freshveggie.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequestMapping("/admin/common")
@Tag(name = "通用接口")
@Validated
@RequiredArgsConstructor
public class CommonController {

    private final AliOssUtil aliOssUtil;

    @PostMapping("/upload")
    @Operation(description = "文件上传接口")
    public Result<String> uploadFile(
            @Parameter(description = "需要上传的文件")
            @File(maxSize = 5, allowFileTypes = {"jpg", "jpeg", "png", "gif"})
            MultipartFile file
    ) {
        String path = aliOssUtil.upload(file);
        return Result.success(path);
    }

}
