package cn.javastack.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAiPracticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiPracticeApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(ChatClient.Builder builder) {
        return args -> {
            ChatClient chatClient = builder.build();
            String response = chatClient.prompt("你是什么模型？").call().content();
            System.out.println(response);
        };
    }

}
