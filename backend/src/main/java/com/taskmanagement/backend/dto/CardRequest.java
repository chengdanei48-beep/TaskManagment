package com.taskmanagement.backend.dto;

import com.taskmanagement.backend.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * カードの作成・更新リクエスト。columnId は作成時のみ使う(更新ではカラムを移動しない)。 labelIds は null なら変更しない(作成時はラベルなし)、空配列ならすべて外す。
 * タイトルは前後の空白を除いた文字数で検証する。
 */
public record CardRequest(
        Long columnId,
        @NotBlank(message = "タイトルは1〜50文字で入力してください")
                @Size(max = 50, message = "タイトルは1〜50文字で入力してください")
                String title,
        @Size(max = 500, message = "詳細説明は500文字までで入力してください") String description,
        LocalDate dueDate,
        Priority priority,
        List<Long> labelIds) {

    public CardRequest {
        title = title == null ? null : title.trim();
    }
}
