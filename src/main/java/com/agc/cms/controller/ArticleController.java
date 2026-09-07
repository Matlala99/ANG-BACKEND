package com.agc.cms.controller;

import com.agc.cms.security.AuthenticatedUser;
import com.agc.cms.security.JwtAuthenticationFilter;
import com.agc.cms.security.SanitizerUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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

    private AuthenticatedUser getAuthUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTR);
    }

    @GetMapping
    public ResponseEntity<?> getArticles() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, title, content, author, article_type, created_at FROM articles ORDER BY created_at DESC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<?> addArticle(@RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        AuthenticatedUser user = getAuthUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String rawTitle = (String) body.get("title");
        String rawContent = (String) body.get("content");
        String rawAuthor = (String) body.getOrDefault("author", user.getUsername());
        String rawArticleType = (String) body.get("article_type");

        if (rawTitle == null || rawContent == null || rawArticleType == null ||
            rawTitle.trim().isEmpty() || rawContent.trim().isEmpty() || rawArticleType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title, Content, and Article Type are required"));
        }

        String title = SanitizerUtils.sanitizeText(rawTitle, 255);
        String content = SanitizerUtils.sanitizeText(rawContent, 10000);
        String author = SanitizerUtils.sanitizeText(rawAuthor, 150);
        String articleType = SanitizerUtils.sanitizeText(rawArticleType, 100);

        jdbcTemplate.update(
            "INSERT INTO articles (title, content, author, article_type) VALUES (?, ?, ?, ?)",
            title, content, author, articleType
        );

        return ResponseEntity.ok(Map.of("success", true));
    }
}
