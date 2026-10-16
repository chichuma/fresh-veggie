package com.freshveggie.dto;

import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

@Data
@Schema(description = "员工信息模型")
public class  StaffDTO implements Serializable {

    @Schema(description = "ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NotNull(groups = Update.class, message = "员工ID不能为空")
    private Long id;

    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 8, message = "用户名长度应在 3 到 8 个字符之间")
    private String username;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 4, message = "姓名长度应在 2 到 4 个字符之间")
    private String name;

    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1(3[0-9]|5[0-35-9]|7[1-35-8]|8[0-9])\\d{8}$", message = "手机号码格式不正确")
    private String phone;

    @Schema(description = "性别 0男 1女", allowableValues = "0, 1")
    @NotNull(message = "性别不能为空")
    @Range(max = 1L, message = "性别输入错误")
    private Integer sex;

    @Schema(description = "身份证号")
    @Pattern(regexp = "^[1-9][0-9]{16}[0-9xX]$", message = "身份证号格式有误")
    @NotBlank(message = "身份证号不能为空")
    private String idNumber;

    @Schema(description = "角色 1管理员 2运营 3分拣员", allowableValues = "1, 2, 3")
    @Range(min = 1L, max = 3L, message = "角色只能是 1管理员 / 2运营 / 3分拣员")
    private Integer role;

}
