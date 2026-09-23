package com.yudong.aistudy.controller;

import com.yudong.aistudy.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test")
    public Result<String> test() {
        return Result.ok("AI Study RAG 项目启动成功");
    }
}
