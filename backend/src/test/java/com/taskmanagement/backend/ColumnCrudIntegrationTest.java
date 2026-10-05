package com.taskmanagement.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ColumnCrudIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;
    @Autowired CardRepository cardRepository;

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

    private List<BoardColumn> columnsOf(String name) {
        Long userId = userRepository.findByUsername(name).orElseThrow().getId();
        return boardColumnRepository.findByUserIdOrderByPosition(userId);
    }

    private ResultActions addColumn(MockHttpSession session, String name) throws Exception {
        return mockMvc.perform(post("/api/columns")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)));
    }

    private ResultActions deleteColumn(MockHttpSession session, Long id) throws Exception {
        return mockMvc.perform(delete("/api/columns/" + id).session(session).with(csrf()));
    }

    private long addCard(MockHttpSession session, Long columnId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void 列を追加すると末尾に並ぶ() throws Exception {
        MockHttpSession session = newSession();

        addColumn(session, "レビュー待ち")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("レビュー待ち"))
                .andExpect(jsonPath("$.position").value(3))
                .andExpect(jsonPath("$.cardCount").value(0));

        mockMvc.perform(get("/api/columns").session(session))
                .andExpect(jsonPath("$[*].name").value(contains("未着手", "進行中", "完了", "レビュー待ち")));
    }

    @Test
    void 列名は1から20文字で_前後の空白は除かれる() throws Exception {
        MockHttpSession session = newSession();

        addColumn(session, "").andExpect(status().isBadRequest());
        addColumn(session, "   ").andExpect(status().isBadRequest());
        addColumn(session, "あ".repeat(21)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/columns")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        addColumn(session, "あ".repeat(20)).andExpect(status().isCreated());
        addColumn(session, "  前後空白  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("前後空白"));
    }

    @Test
    void 列は最大10列まで_超えると409() throws Exception {
        MockHttpSession session = newSession();
        // 初期の3列 + 7列 = 10列
        for (int i = 1; i <= 7; i++) {
            addColumn(session, "列" + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/columns").session(session)).andExpect(jsonPath("$", hasSize(10)));

        addColumn(session, "11列目").andExpect(status().isConflict());
        mockMvc.perform(get("/api/columns").session(session)).andExpect(jsonPath("$", hasSize(10)));

        // 1列消せば、また追加できる
        deleteColumn(session, columnsOf(username).get(0).getId()).andExpect(status().isNoContent());
        addColumn(session, "11列目").andExpect(status().isCreated());
    }

    @Test
    void 列一覧にカード数が含まれる() throws Exception {
        MockHttpSession session = newSession();
        Long first = columnsOf(username).get(0).getId();
        addCard(session, first, "A");
        addCard(session, first, "B");

        mockMvc.perform(get("/api/columns").session(session))
                .andExpect(jsonPath("$[0].cardCount").value(2))
                .andExpect(jsonPath("$[1].cardCount").value(0));
    }

    @Test
    void 列を削除するとカードも消え_残りの列の位置が詰まる() throws Exception {
        MockHttpSession session = newSession();
        List<BoardColumn> columns = columnsOf(username);
        Long first = columns.get(0).getId();
        Long second = columns.get(1).getId();
        long cardInFirst = addCard(session, first, "消えるカード");
        long cardInSecond = addCard(session, second, "残るカード");

        deleteColumn(session, first).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cards/" + cardInFirst).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/" + cardInSecond).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/columns").session(session))
                .andExpect(jsonPath("$[*].name").value(contains("進行中", "完了")))
                .andExpect(jsonPath("$[*].position").value(contains(0, 1)));
        assertEquals(0, cardRepository.findByColumnIdOrderByPosition(first).size());
        // 同じ列をもう一度消すと404
        deleteColumn(session, first).andExpect(status().isNotFound());
    }

    @Test
    void 他人の列は削除できない() throws Exception {
        MockHttpSession owner = newSession();
        Long ownerColumn = columnsOf(username).get(0).getId();
        long card = addCard(owner, ownerColumn, "Aのカード");
        MockHttpSession other = newSession();

        deleteColumn(other, ownerColumn).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cards/" + card).session(owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/columns").session(owner)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void 未ログインは401_CSRFなしは403() throws Exception {
        MockHttpSession session = newSession();
        Long column = columnsOf(username).get(0).getId();

        mockMvc.perform(post("/api/columns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/columns")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/columns/" + column).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/columns/" + column).with(csrf())).andExpect(status().isUnauthorized());
    }
}
