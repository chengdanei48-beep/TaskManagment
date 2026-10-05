package com.taskmanagement.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 登録・ログインのリクエスト。パスワードをログ等に出さないよう toString では伏せる。 */
public record AuthRequest(
        @NotBlank(message = "ユーザー名は1〜50文字で入力してください")
                @Size(max = 50, message = "ユーザー名は1〜50文字で入力してください")
                String username,
        // BCryptは72バイトを超える部分を無視するため上限を設ける
        @NotNull(message = "パスワードは8〜72文字で入力してください")
                @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください")
                String password) {

    @Override
    public String toString() {
        return "AuthRequest[username=" + username + ", password=****]";
    }
}
