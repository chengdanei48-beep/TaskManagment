package com.taskmanagement.backend.repository;

/** 列ごとのカード数(列一覧のN+1クエリを避けるための集計結果)。 */
public record ColumnCardCount(Long columnId, long cardCount) {}
