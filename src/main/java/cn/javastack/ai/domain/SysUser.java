package cn.javastack.ai.domain;

import cn.javastack.ai.domain.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户实体类")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("sys_user")
public class SysUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;                      // 用户ID
    private String username;              // 用户名
    private String password;              // 密码
    private String nickname;              // 昵称
    private String phone;                 // 手机号
    private String email;                 // 邮箱
    @Builder.Default
    private UserStatus status = UserStatus.NORMAL; // 状态
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now(); // 创建时间
    private LocalDateTime updateTime;              // 更新时间
    @Builder.Default
    @TableLogic
    private Integer isDeleted = 0;        // 逻辑删除标志 (0-未删除, 1-已删除)

    @TableField(exist = false)
    private List<UserAddress> addresses;  // 用户地址列表
}
