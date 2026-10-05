package com.taskmanagement.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
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
class CardCrudIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;
    @Autowired CardRepository cardRepository;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
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

    private Long firstColumnId(MockHttpSession session) throws Exception {
        String id = userId(session);
        List<BoardColumn> columns =
                boardColumnRepository.findByUserIdOrderByPosition(Long.valueOf(id));
        return columns.get(0).getId();
    }

    private String userId(MockHttpSession session) throws Exception {
        String body =
                mockMvc.perform(get("/api/auth/me").session(session))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return body.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    private ResultActions createCard(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(
                post("/api/cards")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private static String cardJson(Long columnId, String title) {
        return "{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title);
    }

    private long createdId(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void カードを作成すると列の末尾に追加される() throws Exception {
        MockHttpSession session = newSession();
        Long columnId = firstColumnId(session);

        createCard(session, cardJson(columnId, "一枚目"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("一枚目"))
                .andExpect(jsonPath("$.columnId").value(columnId))
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.priority").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists());
        createCard(session, cardJson(columnId, "二枚目"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(get("/api/cards").session(session))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("一枚目"))
                .andExpect(jsonPath("$[1].title").value("二枚目"));
    }

    @Test
    void 説明_期限日_重要度を指定して作成できる() throws Exception {
        MockHttpSession session = newSession();
        Long columnId = firstColumnId(session);

        createCard(
                        session,
                        "{\"columnId\":%d,\"title\":\"全項目\",\"description\":\"詳細\",\"dueDate\":\"2026-12-31\",\"priority\":\"HIGH\"}"
                                .formatted(columnId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("詳細"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-31"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void 入力が不正なら400() throws Exception {
        MockHttpSession session = newSession();
        Long columnId = firstColumnId(session);

        createCard(session, cardJson(columnId, "")).andExpect(status().isBadRequest());
        createCard(session, cardJson(columnId, "   ")).andExpect(status().isBadRequest());
        createCard(session, cardJson(columnId, "あ".repeat(51))).andExpect(status().isBadRequest());
        createCard(session, "{\"columnId\":%d}".formatted(columnId))
                .andExpect(status().isBadRequest());
        createCard(session, "{\"title\":\"カラム無し\"}").andExpect(status().isBadRequest());
        createCard(
                        session,
                        "{\"columnId\":%d,\"title\":\"x\",\"description\":\"%s\"}"
                                .formatted(columnId, "a".repeat(501)))
                .andExpect(status().isBadRequest());
        createCard(
                        session,
                        "{\"columnId\":%d,\"title\":\"x\",\"priority\":\"URGENT\"}"
                                .formatted(columnId))
                .andExpect(status().isBadRequest());

        // 境界値(50文字・500文字)は作成できる
        createCard(
                        session,
                        "{\"columnId\":%d,\"title\":\"%s\",\"description\":\"%s\"}"
                                .formatted(columnId, "あ".repeat(50), "a".repeat(500)))
                .andExpect(status().isCreated());
    }

    @Test
    void 他人のカラムには作成できない() throws Exception {
        MockHttpSession owner = newSession();
        MockHttpSession other = newSession();
        Long ownerColumn = firstColumnId(owner);

        createCard(other, cardJson(ownerColumn, "侵入")).andExpect(status().isNotFound());
        assertTrue(cardRepository.findByColumnIdOrderByPosition(ownerColumn).isEmpty());
    }

    @Test
    void カードを更新できる_カラムは移動しない() throws Exception {
        MockHttpSession session = newSession();
        Long columnId = firstColumnId(session);
        long id = createdId(createCard(session, cardJson(columnId, "更新前")));

        mockMvc.perform(
                        put("/api/cards/" + id)
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"columnId\":999999,\"title\":\"更新後\",\"description\":\"説明\","
                                                + "\"dueDate\":\"2026-11-01\",\"priority\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("更新後"))
                .andExpect(jsonPath("$.description").value("説明"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-01"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.columnId").value(columnId));

        // 空の説明・未設定の重要度/期限に戻せる
        mockMvc.perform(
                        put("/api/cards/" + id)
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"更新後\",\"description\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.priority").doesNotExist());
    }

    @Test
    void 更新の入力が不正なら400_存在しないIDは404() throws Exception {
        MockHttpSession session = newSession();
        long id = createdId(createCard(session, cardJson(firstColumnId(session), "元")));

        mockMvc.perform(
                        put("/api/cards/" + id)
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        put("/api/cards/999999999")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 他人のカードは更新も削除もできない() throws Exception {
        MockHttpSession owner = newSession();
        MockHttpSession other = newSession();
        long id = createdId(createCard(owner, cardJson(firstColumnId(owner), "Aのカード")));

        mockMvc.perform(
                        put("/api/cards/" + id)
                                .session(other)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"乗っ取り\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cards/" + id).session(other).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cards/" + id).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aのカード"));
    }

    @Test
    void カードを削除できる() throws Exception {
        MockHttpSession session = newSession();
        long id = createdId(createCard(session, cardJson(firstColumnId(session), "消す")));

        mockMvc.perform(delete("/api/cards/" + id).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cards/" + id).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cards/" + id).session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 未ログインは401_CSRFトークンなしは403() throws Exception {
        mockMvc.perform(
                        post("/api/cards")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"columnId\":1,\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = newSession();
        mockMvc.perform(
                        post("/api/cards")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cardJson(firstColumnId(session), "x")))
                .andExpect(status().isForbidden());
    }
}
