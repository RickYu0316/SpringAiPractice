package cn.javastack.ai.dto.custom;

import lombok.Data;

/**
 * 通用聊天请求参数
 */
@Data
public class CustomChatRequest {

    /**
     * 消息内容
     */
    private String message;

    /**
     * 最大 Token 数
     */
    private Integer maxTokens = 8192;

    /**
     * 口吻
     */
    private String voice = "聊天AI助手";
}

