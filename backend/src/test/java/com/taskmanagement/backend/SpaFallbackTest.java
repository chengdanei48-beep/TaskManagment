package com.taskmanagement.backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanagement.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** 同梱フロントエンド(src/test/resources/static/index.html を代用)の配信とSPAフォールバックの確認。 */
@SpringBootTest
@AutoConfigureMockMvc
class SpaFallbackTest {

    private static final String MARKER = "spa-index-for-test";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    private String username;

    @AfterEach
    void cleanUp() {
        if (username != null) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
    }

    @Test
    void ルートはindexhtmlへ転送され_indexhtmlはフロントエンドを返す() throws Exception {
        // "/" はウェルカムページとして index.html へ転送される(MockMvcは転送先まで辿らないため転送先を確認)
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(MARKER)));
    }

    @Test
    void 画面のURLを直接開いてもフロントエンドが返る() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(MARKER)));
        mockMvc.perform(get("/some/deep/path"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(MARKER)));
    }

    @Test
    void 拡張子付きの存在しないファイルは404() throws Exception {
        mockMvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/favicon-missing.svg")).andExpect(status().isNotFound());
    }

    @Test
    void 存在しないAPIはHTMLを返さない() throws Exception {
        // 未ログインは認証で弾かれる(401)
        mockMvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());

        // ログイン済みなら404(index.htmlにはならない)
        username = "test_" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session =
                (MockHttpSession)
                        mockMvc.perform(
                                        post("/api/auth/register")
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"username\":\"%s\",\"password\":\"password123\"}"
                                                                .formatted(username)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getRequest()
                                .getSession(false);
        mockMvc.perform(get("/api/no-such-endpoint").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(MARKER))));
    }
}
