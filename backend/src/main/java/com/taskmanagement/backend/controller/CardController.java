package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.service.CardService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/cards")
    public List<CardResponse> search(
            @RequestParam(required = false) Long columnId,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String keyword) {
        return cardService.search(columnId, priority, keyword);
    }

    @GetMapping("/api/cards/{id}")
    public ResponseEntity<CardResponse> findById(@PathVariable Long id) {
        return cardService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
