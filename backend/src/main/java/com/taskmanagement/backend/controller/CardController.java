package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.CardMoveRequest;
import com.taskmanagement.backend.dto.CardRequest;
import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.CardService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public List<CardResponse> search(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(required = false) Long columnId,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String keyword) {
        return cardService.search(user.getId(), columnId, priority, keyword);
    }

    @GetMapping("/{id}")
    public CardResponse findById(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return cardService.findById(user.getId(), id);
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(
            @AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody CardRequest request) {
        CardResponse created = cardService.create(user.getId(), request);
        return ResponseEntity.created(URI.create("/api/cards/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CardResponse update(
            @AuthenticationPrincipal AppUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CardRequest request) {
        return cardService.update(user.getId(), id, request);
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<Void> move(
            @AuthenticationPrincipal AppUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CardMoveRequest request) {
        cardService.move(user.getId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        cardService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
