package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Priority;
import java.time.LocalDate;
import java.util.List;

/**
 * カードの作成・更新リクエスト。columnId は作成時のみ使う(更新ではカラムを移動しない)。
 * labelIds は null なら変更しない(作成時はラベルなし)、空配列ならすべて外す。
 */
public record CardRequest(
        Long columnId,
        String title,
        String description,
        LocalDate dueDate,
        Priority priority,
        List<Long> labelIds) {}
