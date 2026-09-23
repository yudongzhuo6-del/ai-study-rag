package com.yudong.aistudy.model.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LlmMessageDTO {//一条聊天记录

    private String role;//谁说的
    private String content;//说了什么
}
