package com.taskmanagement.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanagement.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class LabelCrudIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // labels は users への ON DELETE CASCADE で消える
        createdUsernames.forEach(
                name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
    }

    /** 新規ユーザーを登録し、ログイン済みのセッションを返す。 */
    private MockHttpSession newSession() throws Exception {
        String name = "test_" + UUID.randomUUID().toString().substring(0, 8);
        createdUsernames.add(name);
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/register")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"username\":\"%s\",\"password\":\"password123\"}"
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions createLabel(MockHttpSession session, String name, String color)
            throws Exception {
        return mockMvc.perform(
                post("/api/labels")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"color\":\"%s\"}".formatted(name, color)));
    }

    private long createdId(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void ラベルを登録すると一覧に表示される() throws Exception {
        MockHttpSession session = newSession();

        createLabel(session, "緊急", "#ff0000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("緊急"))
                .andExpect(jsonPath("$.color").value("#FF0000"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/labels").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("緊急"));
    }

    @Test
    void 名前が空または21文字以上なら400() throws Exception {
        MockHttpSession session = newSession();

        createLabel(session, "  ", "#000000").andExpect(status().isBadRequest());
        createLabel(session, "あ".repeat(21), "#000000").andExpect(status().isBadRequest());
        createLabel(session, "あ".repeat(20), "#000000").andExpect(status().isCreated());
    }

    @Test
    void 色の形式が不正なら400() throws Exception {
        MockHttpSession session = newSession();

        createLabel(session, "色なし", "red").andExpect(status().isBadRequest());
        createLabel(session, "桁不足", "#fff").andExpect(status().isBadRequest());
    }

    @Test
    void 同じ名前のラベルは409だが別ユーザーなら登録できる() throws Exception {
        MockHttpSession session = newSession();
        MockHttpSession other = newSession();

        createLabel(session, "重複", "#111111").andExpect(status().isCreated());
        createLabel(session, "重複", "#222222").andExpect(status().isConflict());
        createLabel(other, "重複", "#111111").andExpect(status().isCreated());
    }

    @Test
    void 未ログインなら401() throws Exception {
        mockMvc.perform(get("/api/labels")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/labels")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"x\",\"color\":\"#000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ラベルを削除できる_他人のラベルは404() throws Exception {
        MockHttpSession session = newSession();
        MockHttpSession other = newSession();
        long id = createdId(createLabel(session, "消す", "#333333"));

        mockMvc.perform(delete("/api/labels/" + id).session(other).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/labels/" + id).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/labels").session(session)).andExpect(jsonPath("$", hasSize(0)));
    }
}
