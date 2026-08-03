package com.agc.cms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final JdbcTemplate jdbcTemplate;

    public ArticleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getArticles() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM articles ORDER BY created_at DESC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<?> addArticle(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String author = (String) body.getOrDefault("author", "Civil Legal Division");
        String articleType = (String) body.get("article_type");

        if (title == null || content == null || articleType == null ||
            title.trim().isEmpty() || content.trim().isEmpty() || articleType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title, Content, and Article Type are required"));
        }

        jdbcTemplate.update(
            "INSERT INTO articles (title, content, author, article_type) VALUES (?, ?, ?, ?)",
            title, content, author, articleType
        );

        return ResponseEntity.ok(Map.of("success", true));
    }
}
