package com.sjs.image.controller;

import com.sjs.image.ai.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 能力元数据接口。
 * 暴露超分算法与支持倍率，供前端动态渲染选项（单点事实来源，避免前端硬编码）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/super-res/scales")
    public ResponseEntity<Map<String, List<Integer>>> superResScales() {
        return ResponseEntity.ok(aiService.superResScales());
    }
}