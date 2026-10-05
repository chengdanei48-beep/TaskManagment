package com.taskmanagement.backend.dto;

/** 列の並び替えリクエスト。by に並び替えの基準を指定する。 */
public record ColumnSortRequest(SortKey by) {

    public enum SortKey {
        PRIORITY,
        DUE_DATE
    }
}
