package cn.javastack.ai.service.impl;

import cn.javastack.ai.domain.UserAddress;
import cn.javastack.ai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final ChatClient deepSeekChatClient;

    @Override
    public List<UserAddress> getUserServiceList() {
        return deepSeekChatClient.prompt()
                .user("获取最近活跃的前 5 个用户的住址列表")
                .call()
                .entity(new ParameterizedTypeReference<List<UserAddress>>() {});
    }

    @Override
    public List<UserAddress> getUserServiceList(String message) {
        return deepSeekChatClient.prompt()
                .user(message)
                .call()
                .entity(new ParameterizedTypeReference<List<UserAddress>>() {});
    }
}
