package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Label;
import com.taskmanagement.backend.entity.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record CardResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        LocalDate dueDate,
        Priority priority,
        Integer position,
        LocalDateTime createdAt,
        List<LabelResponse> labels) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getColumn().getId(),
                card.getTitle(),
                card.getDescription(),
                card.getDueDate(),
                card.getPriority(),
                card.getPosition(),
                card.getCreatedAt(),
                card.getLabels().stream()
                        .sorted(Comparator.comparing(Label::getId))
                        .map(LabelResponse::from)
                        .toList());
    }
}
