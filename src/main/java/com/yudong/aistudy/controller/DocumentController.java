package com.yudong.aistudy.controller;

import com.yudong.aistudy.common.result.Result;
import com.yudong.aistudy.common.exception.BusinessException;
import com.yudong.aistudy.mapper.DocumentMapper;
import com.yudong.aistudy.model.dto.document.DocumentAddDTO;
import com.yudong.aistudy.model.DocumentStatus;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.vo.document.DocumentDeleteResultVO;
import com.yudong.aistudy.model.vo.document.DocumentProcessStatusVO;
import com.yudong.aistudy.service.DocumentProcessingService;
import com.yudong.aistudy.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/document")
public class DocumentController {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @GetMapping("/{id}/status")
    public Result<DocumentProcessStatusVO> status(@PathVariable Long id,
                                                   @RequestParam("userId") Long userId) {
        return Result.ok(documentProcessingService.getStatus(id, userId));
    }

    @PostMapping("/{id}/retry")
    public Result<DocumentProcessStatusVO> retry(@PathVariable Long id,
                                                  @RequestParam("userId") Long userId) {
        return Result.ok(documentProcessingService.retry(id, userId));
    }

    // 查询列表
    @GetMapping("/list")
    public Result<List<Document>> list() {
        List<Document> list = documentMapper.selectList(null);
        return Result.ok(list);
    }

    // 新增文档
    @PostMapping("/add")
    public Result add(@RequestBody DocumentAddDTO dto) {
        documentService.add(dto);
        return Result.ok();
    }

    // 删除文档，同时删除对应 chunk
    @DeleteMapping("/delete/{id}")
    public Result<DocumentDeleteResultVO> delete(@PathVariable Long id,
                                                  @RequestParam("userId") Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userId is required and must be positive");
        }
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Document not found");
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Document does not belong to this user");
        }
        if (document.getStatus() != null
                && (document.getStatus() == DocumentStatus.PENDING
                || document.getStatus() == DocumentStatus.PROCESSING)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Document is still being processed and cannot be deleted");
        }
        DocumentDeleteResultVO result = documentService.deleteById(id);
        return Result.ok(result);
    }
}
