package com.taskmanagement.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ラベルの作成リクエスト。name は前後の空白を除いた文字数で検証し、color は #RRGGBB 形式。 */
public record LabelRequest(
        @NotBlank(message = "ラベル名は1〜20文字で入力してください")
                @Size(max = 20, message = "ラベル名は1〜20文字で入力してください")
                String name,
        @NotNull(message = "色は #RRGGBB 形式で指定してください")
                @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "色は #RRGGBB 形式で指定してください")
                String color) {

    public LabelRequest {
        name = name == null ? null : name.trim();
    }
}
