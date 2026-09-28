package cn.javastack.ai.service;

import cn.javastack.ai.domain.SysUser;

import java.util.List;

/**
 * 用户管理 Service
 */
public interface SysUserService {

    /**
     * 用户列表
     */
    List<SysUser> list();

    /**
     * 用户详情（含地址列表）
     */
    SysUser detail(Long id);

    /**
     * 查询最近活跃用户
     */
    List<SysUser> selectRecentActive(int limit);

    /**
     * 根据手机号查询用户
     */
    SysUser selectByPhone(String phone);

    /**
     * 新增用户
     */
    boolean save(SysUser user);

    /**
     * 修改用户
     */
    boolean updateById(SysUser user);

    /**
     * 删除用户（逻辑删除）
     */
    boolean deleteById(Long id);
}
