package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.BoardColumn;

/** cardCount は列内のカード数(列の削除確認に使う)。 */
public record ColumnResponse(Long id, String name, Integer position, long cardCount) {

    public static ColumnResponse from(BoardColumn column, long cardCount) {
        return new ColumnResponse(column.getId(), column.getName(), column.getPosition(), cardCount);
    }
}
