package com.taskmanagement.backend.controller;

import com.taskmanagement.backend.dto.ColumnResponse;
import com.taskmanagement.backend.dto.ColumnSortRequest;
import com.taskmanagement.backend.security.AppUserDetails;
import com.taskmanagement.backend.service.ColumnService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ColumnController {

    private final ColumnService columnService;

    public ColumnController(ColumnService columnService) {
        this.columnService = columnService;
    }

    @GetMapping("/api/columns")
    public List<ColumnResponse> findAll(@AuthenticationPrincipal AppUserDetails user) {
        return columnService.findAll(user.getId());
    }

    @PutMapping("/api/columns/{id}/sort")
    public ResponseEntity<Void> sort(
            @AuthenticationPrincipal AppUserDetails user,
            @PathVariable Long id,
            @RequestBody ColumnSortRequest request) {
        return columnService.sortCards(user.getId(), id, request.by())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
