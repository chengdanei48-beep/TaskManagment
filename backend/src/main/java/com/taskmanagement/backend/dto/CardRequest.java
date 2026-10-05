package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Priority;
import java.time.LocalDate;

/** カードの作成・更新リクエスト。columnId は作成時のみ使う(更新ではカラムを移動しない)。 */
public record CardRequest(
        Long columnId, String title, String description, LocalDate dueDate, Priority priority) {}
