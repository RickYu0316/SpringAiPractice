package cn.javastack.ai.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    NORMAL(0, "正常"),
    DISABLED(1, "禁用");

    @EnumValue
    private final int code;

    private final String desc;
}
