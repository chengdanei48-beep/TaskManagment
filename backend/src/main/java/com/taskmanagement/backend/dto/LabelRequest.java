package com.taskmanagement.backend.dto;

/** ラベルの作成リクエスト。color は #RRGGBB 形式。 */
public record LabelRequest(String name, String color) {}
