package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Label;
import java.time.LocalDateTime;

public record LabelResponse(Long id, String name, String color, LocalDateTime createdAt) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(
                label.getId(), label.getName(), label.getColor(), label.getCreatedAt());
    }
}
