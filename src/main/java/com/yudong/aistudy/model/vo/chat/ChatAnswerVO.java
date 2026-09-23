package com.yudong.aistudy.model.vo.chat;

import lombok.Data;

import java.util.List;

@Data
public class ChatAnswerVO {

    private String question;

    private String answer;

    private Integer retrievedCount;

    private Integer vectorDimension;

    private List<ChatSourceVO> sources;
}
