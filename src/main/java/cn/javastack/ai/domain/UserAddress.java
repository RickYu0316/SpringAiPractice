package cn.javastack.ai.domain;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "用户地址实体类")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户地址ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;                      // 用户地址ID
    @Schema(description = "用户ID")
    private Long userId;                  // 用户ID
    private String receiverName;          // 收货人姓名
    private String receiverPhone;         // 收货人电话
    private String country;               // 国家
    private String province;              // 省份/直辖市
    private String city;                  // 城市
    private String district;              // 区县
    private String detailAddress;         // 详细地址
    private String postalCode;            // 邮政编码
    @Builder.Default
    private Integer isDefault = 0;        // 是否为默认地址 (0-否, 1-是)
    private String addressTag;            // 地址标签
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now(); // 创建时间
    private LocalDateTime updateTime;              // 更新时间
    @Builder.Default
    private Integer isDeleted = 0;        // 逻辑删除标志 (0-未删除, 1-已删除)

    /**
     * 获取完整地址
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();

        if (country != null && !country.trim().isEmpty()) {
            sb.append(country);
        }

        if (province != null && !province.trim().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(province);
        }

        if (city != null && !city.trim().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(city);
        }

        if (district != null && !district.trim().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(district);
        }

        if (detailAddress != null && !detailAddress.trim().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(detailAddress);
        }

        return sb.toString();
    }

    /**
     * 判断是否为默认地址
     */
    public boolean isDefaultAddress() {
        return isDefault != null && isDefault == 1;
    }
}
