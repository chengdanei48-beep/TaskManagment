package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        LocalDate dueDate,
        Priority priority,
        Integer position,
        LocalDateTime createdAt) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getColumn().getId(),
                card.getTitle(),
                card.getDescription(),
                card.getDueDate(),
                card.getPriority(),
                card.getPosition(),
                card.getCreatedAt());
    }
}
