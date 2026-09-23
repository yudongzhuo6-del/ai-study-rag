package com.yudong.aistudy.model.dto.llm;

import lombok.Data;

import java.util.List;

@Data
public class LlmChatResponseDTO {

    private List<Choice> choices;//答案列表

    @Data
    public static class Choice {
        private Message message;//答案消息
    }

    @Data
    public static class Message {//一条消息
        private String role;//用户
        private String content;
    }
}
