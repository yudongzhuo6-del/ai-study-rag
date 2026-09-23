package com.yudong.aistudy.rag.query;

public interface QueryRewriteService {

    QueryRewriteResult rewrite(String question);
}
