package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.ColumnRequest;
import com.taskmanagement.backend.dto.ColumnResponse;
import com.taskmanagement.backend.dto.ColumnSortRequest;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.ColumnService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/columns")
public class ColumnController {

    private final ColumnService columnService;

    public ColumnController(ColumnService columnService) {
        this.columnService = columnService;
    }

    @GetMapping
    public List<ColumnResponse> findAll(@AuthenticationPrincipal AppUserDetails user) {
        return columnService.findAll(user.getId());
    }

    @PostMapping
    public ResponseEntity<ColumnResponse> create(
            @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody ColumnRequest request) {
        ColumnResponse created = columnService.create(user.getId(), request);
        return ResponseEntity.created(URI.create("/api/columns/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        columnService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sort")
    public ResponseEntity<Void> sort(
            @AuthenticationPrincipal AppUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ColumnSortRequest request) {
        columnService.sortCards(user.getId(), id, request.by());
        return ResponseEntity.noContent().build();
    }
}
