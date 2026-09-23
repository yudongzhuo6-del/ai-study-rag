package com.yudong.aistudy.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_case")
public class EvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    private String question;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("golden_chunks")
    private String goldenChunks;

    @TableField("required_keywords")
    private String requiredKeywords;

    @TableField("question_type")
    private String questionType;

    private String difficulty;

    private Integer enabled;

    @TableField("create_time")
    private LocalDateTime createTime;
}
