package cn.javastack.ai.service.impl;

import cn.javastack.ai.domain.SysUser;
import cn.javastack.ai.domain.UserAddress;
import cn.javastack.ai.mapper.SysUserMapper;
import cn.javastack.ai.mapper.UserAddressMapper;
import cn.javastack.ai.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理 ServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    private final UserAddressMapper userAddressMapper;

    @Override
    public List<SysUser> list() {
        return sysUserMapper.selectList(null);
    }

    @Override
    public SysUser detail(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            log.warn("用户不存在，id：{}", id);
            return null;
        }
        // 关联查询用户地址列表
        List<UserAddress> addresses = userAddressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, id));
        user.setAddresses(addresses);
        return user;
    }

    @Override
    public List<SysUser> selectRecentActive(int limit) {
        return sysUserMapper.selectRecentActive(limit);
    }

    @Override
    public SysUser selectByPhone(String phone) {
        return sysUserMapper.selectByPhone(phone);
    }

    @Override
    public boolean save(SysUser user) {
        // 防止用户名重复
        if (sysUserMapper.countByUsername(user.getUsername()) > 0) {
            log.warn("用户名已存在：{}", user.getUsername());
            return false;
        }
        return sysUserMapper.insert(user) > 0;
    }

    @Override
    public boolean updateById(SysUser user) {
        return sysUserMapper.updateById(user) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return sysUserMapper.deleteById(id) > 0;
    }
}
