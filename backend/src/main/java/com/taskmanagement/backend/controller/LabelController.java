package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.LabelRequest;
import com.taskmanagement.backend.dto.LabelResponse;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.LabelService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    public List<LabelResponse> findAll(@AuthenticationPrincipal AppUserDetails user) {
        return labelService.findAll(user.getId());
    }

    @PostMapping
    public ResponseEntity<LabelResponse> create(
            @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody LabelRequest request) {
        LabelResponse created = labelService.create(user.getId(), request);
        return ResponseEntity.created(URI.create("/api/labels/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        labelService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
