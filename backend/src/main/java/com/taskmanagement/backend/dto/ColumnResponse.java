package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.BoardColumn;

public record ColumnResponse(Long id, String name, Integer position) {

    public static ColumnResponse from(BoardColumn column) {
        return new ColumnResponse(column.getId(), column.getName(), column.getPosition());
    }
}
