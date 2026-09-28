package cn.javastack.ai.controller;

import cn.javastack.ai.domain.SysUser;
import cn.javastack.ai.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理 Controller
 */
@Tag(name = "用户管理接口", description = "用户管理接口")
@Slf4j
@RestController
@RequestMapping("/user")
public class SysUserController {

    @Resource
    private SysUserService sysUserService;

    @Operation(summary = "用户列表")
    @GetMapping("/list")
    public List<SysUser> list() {
        return sysUserService.list();
    }

    @Operation(summary = "用户详情（含地址列表）")
    @GetMapping("/detail")
    public SysUser detail(@Parameter(description = "用户ID") @RequestParam(value = "id") Long id) {
        return sysUserService.detail(id);
    }

    @Operation(summary = "最近活跃用户")
    @GetMapping("/recent")
    public List<SysUser> recent(@Parameter(description = "条数") @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return sysUserService.selectRecentActive(limit);
    }

    @Operation(summary = "根据手机号查询用户")
    @GetMapping("/byPhone")
    public SysUser byPhone(@Parameter(description = "手机号") @RequestParam(value = "phone") String phone) {
        return sysUserService.selectByPhone(phone);
    }

    @Operation(summary = "新增用户")
    @PostMapping("/add")
    public boolean add(@RequestBody SysUser user) {
        return sysUserService.save(user);
    }

    @Operation(summary = "修改用户")
    @PutMapping("/update")
    public boolean update(@RequestBody SysUser user) {
        return sysUserService.updateById(user);
    }

    @Operation(summary = "删除用户（逻辑删除）")
    @DeleteMapping("/delete")
    public boolean delete(@Parameter(description = "用户ID") @RequestParam(value = "id") Long id) {
        return sysUserService.deleteById(id);
    }
}
