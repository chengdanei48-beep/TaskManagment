package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.LabelRequest;
import com.taskmanagement.backend.dto.LabelResponse;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.LabelService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping("/api/labels")
    public List<LabelResponse> findAll(@AuthenticationPrincipal AppUserDetails user) {
        return labelService.findAll(user.getId());
    }

    @PostMapping("/api/labels")
    public ResponseEntity<LabelResponse> create(
            @AuthenticationPrincipal AppUserDetails user, @RequestBody LabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labelService.create(user.getId(), request));
    }

    @DeleteMapping("/api/labels/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        return labelService.delete(user.getId(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
