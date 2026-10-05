package com.taskmanagement.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 列の作成リクエスト。列名は前後の空白を除いた文字数で検証する。 */
public record ColumnRequest(
        @NotBlank(message = "列名は1〜20文字で入力してください") @Size(max = 20, message = "列名は1〜20文字で入力してください")
                String name) {

    public ColumnRequest {
        name = name == null ? null : name.trim();
    }
}
