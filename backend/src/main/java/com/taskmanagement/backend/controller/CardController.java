package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.CardRequest;
import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.CardService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(required = false) Long columnId,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String keyword) {
        return cardService.search(user.getId(), columnId, priority, keyword);
    }

    @GetMapping("/api/cards/{id}")
    public ResponseEntity<CardResponse> findById(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return cardService.findById(user.getId(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/cards")
    public ResponseEntity<CardResponse> create(
            @AuthenticationPrincipal AppUserDetails user, @RequestBody CardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.create(user.getId(), request));
    }

    @PutMapping("/api/cards/{id}")
    public ResponseEntity<CardResponse> update(
            @AuthenticationPrincipal AppUserDetails user,
            @PathVariable Long id,
            @RequestBody CardRequest request) {
        return cardService.update(user.getId(), id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/cards/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return cardService.delete(user.getId(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
