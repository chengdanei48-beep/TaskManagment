package com.taskmanagement.backend.dto;

import jakarta.validation.constraints.NotNull;

/** 列の並び替えリクエスト。by に並び替えの基準を指定する。 */
public record ColumnSortRequest(@NotNull(message = "並び替えの基準を指定してください") SortKey by) {

    public enum SortKey {
        PRIORITY,
        DUE_DATE
    }
}
