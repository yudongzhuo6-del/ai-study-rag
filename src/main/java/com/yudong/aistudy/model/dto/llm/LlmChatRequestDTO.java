package com.yudong.aistudy.model.dto.llm;

import lombok.Data;

import java.util.List;

@Data
public class LlmChatRequestDTO {//给ai的请求包

    private String model;//哪个ai
    private List<LlmMessageDTO> messages;//聊天记录
}
