package com.yudong.aistudy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yudong.aistudy.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
