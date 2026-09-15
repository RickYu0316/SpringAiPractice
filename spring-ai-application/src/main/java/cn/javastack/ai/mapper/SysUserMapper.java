package cn.javastack.ai.mapper;

import cn.javastack.ai.domain.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 统计用户名数量
     */
    long countByUsername(String username);

    /**
     * 根据手机号查询用户
     */
    SysUser selectByPhone(String phone);

    /**
     * 查询最近活跃用户
     */
    List<SysUser> selectRecentActive(@Param("limit") int limit);
}
