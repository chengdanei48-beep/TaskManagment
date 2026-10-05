package com.taskmanagement.backend;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.repository.BoardColumnRepository;
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
class ColumnSortIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;

    private final List<String> createdUsernames = new ArrayList<>();
    private String username;

    @AfterEach
    void cleanUp() {
        createdUsernames.forEach(
                name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
    }

    private MockHttpSession newSession() throws Exception {
        username = "test_" + UUID.randomUUID().toString().substring(0, 8);
        createdUsernames.add(username);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long firstColumnId() {
        Long userId = userRepository.findByUsername(username).orElseThrow().getId();
        List<BoardColumn> columns = boardColumnRepository.findByUserIdOrderByPosition(userId);
        return columns.get(0).getId();
    }

    private void addCard(MockHttpSession session, Long columnId, String title, String priority, String dueDate)
            throws Exception {
        String json = "{\"columnId\":%d,\"title\":\"%s\",\"priority\":%s,\"dueDate\":%s}"
                .formatted(
                        columnId,
                        title,
                        priority == null ? "null" : "\"" + priority + "\"",
                        dueDate == null ? "null" : "\"" + dueDate + "\"");
        mockMvc.perform(post("/api/cards")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private ResultActions sort(MockHttpSession session, Long columnId, String body) throws Exception {
        return mockMvc.perform(put("/api/columns/" + columnId + "/sort")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertOrder(MockHttpSession session, Long columnId, String... titles) throws Exception {
        Integer[] positions = new Integer[titles.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = i;
        }
        mockMvc.perform(get("/api/cards?columnId=" + columnId).session(session))
                .andExpect(jsonPath("$[*].title").value(contains((Object[]) titles)))
                .andExpect(jsonPath("$[*].position").value(contains((Object[]) positions)));
    }

    @Test
    void 優先度順は高_中_低_未設定の順で_同順位は元の並びを保つ() throws Exception {
        MockHttpSession session = newSession();
        Long col = firstColumnId();
        addCard(session, col, "未設定1", null, null);
        addCard(session, col, "低", "LOW", null);
        addCard(session, col, "中1", "MEDIUM", null);
        addCard(session, col, "高", "HIGH", null);
        addCard(session, col, "中2", "MEDIUM", null);
        addCard(session, col, "未設定2", null, null);

        sort(session, col, "{\"by\":\"PRIORITY\"}").andExpect(status().isNoContent());

        assertOrder(session, col, "高", "中1", "中2", "低", "未設定1", "未設定2");
    }

    @Test
    void 期限順は近い順で_期限未設定は最後() throws Exception {
        MockHttpSession session = newSession();
        Long col = firstColumnId();
        addCard(session, col, "未設定", null, null);
        addCard(session, col, "遠い", null, "2027-01-01");
        addCard(session, col, "過去", null, "2020-05-05");
        addCard(session, col, "近い", null, "2026-10-10");
        addCard(session, col, "近い2", null, "2026-10-10");

        sort(session, col, "{\"by\":\"DUE_DATE\"}").andExpect(status().isNoContent());

        assertOrder(session, col, "過去", "近い", "近い2", "遠い", "未設定");
    }

    @Test
    void カードがない列でも成功する() throws Exception {
        MockHttpSession session = newSession();
        sort(session, firstColumnId(), "{\"by\":\"PRIORITY\"}").andExpect(status().isNoContent());
    }

    @Test
    void 基準が未指定または不正なら400() throws Exception {
        MockHttpSession session = newSession();
        Long col = firstColumnId();
        sort(session, col, "{}").andExpect(status().isBadRequest());
        sort(session, col, "{\"by\":\"TITLE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 他人の列や存在しない列は404() throws Exception {
        MockHttpSession owner = newSession();
        Long ownerCol = firstColumnId();
        addCard(owner, ownerCol, "低", "LOW", null);
        addCard(owner, ownerCol, "高", "HIGH", null);
        MockHttpSession other = newSession();

        sort(other, ownerCol, "{\"by\":\"PRIORITY\"}").andExpect(status().isNotFound());
        sort(other, 999999999L, "{\"by\":\"PRIORITY\"}").andExpect(status().isNotFound());
        // 他人の列の並びは変わらない
        assertOrder(owner, ownerCol, "低", "高");
    }

    @Test
    void 未ログインは401_CSRFなしは403() throws Exception {
        MockHttpSession session = newSession();
        Long col = firstColumnId();

        mockMvc.perform(put("/api/columns/" + col + "/sort")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"by\":\"PRIORITY\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/columns/" + col + "/sort")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"by\":\"PRIORITY\"}"))
                .andExpect(status().isForbidden());
    }
}
