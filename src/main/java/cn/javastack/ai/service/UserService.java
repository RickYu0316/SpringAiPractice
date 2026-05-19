package cn.javastack.ai.service;

import cn.javastack.ai.domain.UserAddress;

import java.util.List;

public interface UserService {

    List<UserAddress> getUserServiceList();

    List<UserAddress> getUserServiceList(String message);
}
