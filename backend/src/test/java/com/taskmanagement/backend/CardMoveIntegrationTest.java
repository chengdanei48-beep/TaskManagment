package com.taskmanagement.backend;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
class CardMoveIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUsernames.forEach(
                name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
    }

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

    private List<BoardColumn> columnsOf(String username) {
        Long userId = userRepository.findByUsername(username).orElseThrow().getId();
        return boardColumnRepository.findByUserIdOrderByPosition(userId);
    }

    private List<BoardColumn> columnsOf(MockHttpSession session) throws Exception {
        String body =
                mockMvc.perform(get("/api/auth/me").session(session))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return columnsOf(body.replaceAll(".*\"username\":\"([^\"]+)\".*", "$1"));
    }

    private long addCard(MockHttpSession session, Long columnId, String title) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/cards")
                                        .session(session)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"columnId\":%d,\"title\":\"%s\"}"
                                                        .formatted(columnId, title)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions move(
            MockHttpSession session, long cardId, Long columnId, Long beforeCardId)
            throws Exception {
        return mockMvc.perform(
                put("/api/cards/" + cardId + "/move")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"columnId\":%s,\"beforeCardId\":%s}"
                                        .formatted(columnId, beforeCardId)));
    }

    private void assertColumn(MockHttpSession session, Long columnId, String... titles)
            throws Exception {
        mockMvc.perform(get("/api/cards?columnId=" + columnId).session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[*].title")
                                .value(titles.length == 0 ? empty() : contains((Object[]) titles)))
                .andExpect(jsonPath("$[*].position").value(positions(titles.length)));
    }

    private static org.hamcrest.Matcher<?> positions(int n) {
        if (n == 0) {
            return empty();
        }
        Integer[] expected = new Integer[n];
        for (int i = 0; i < n; i++) {
            expected[i] = i;
        }
        return contains((Object[]) expected);
    }

    @Test
    void 同じ列の中で手前に挿入して並び替えられる() throws Exception {
        MockHttpSession session = newSession();
        Long col = columnsOf(session).get(0).getId();
        long a = addCard(session, col, "A");
        addCard(session, col, "B");
        long c = addCard(session, col, "C");

        move(session, c, col, a).andExpect(status().isNoContent());
        assertColumn(session, col, "C", "A", "B");
    }

    @Test
    void beforeCardIdがnullなら列の末尾に移動する() throws Exception {
        MockHttpSession session = newSession();
        Long col = columnsOf(session).get(0).getId();
        long a = addCard(session, col, "A");
        addCard(session, col, "B");
        addCard(session, col, "C");

        move(session, a, col, null).andExpect(status().isNoContent());
        assertColumn(session, col, "B", "C", "A");
    }

    @Test
    void 別の列の指定位置に移動し_移動元の位置も詰められる() throws Exception {
        MockHttpSession session = newSession();
        List<BoardColumn> columns = columnsOf(session);
        Long from = columns.get(0).getId();
        Long to = columns.get(1).getId();
        long a = addCard(session, from, "A");
        addCard(session, from, "B");
        addCard(session, from, "C");
        long x = addCard(session, to, "X");
        addCard(session, to, "Y");

        // A を X の手前へ
        move(session, a, to, x).andExpect(status().isNoContent());
        assertColumn(session, to, "A", "X", "Y");
        assertColumn(session, from, "B", "C");
        mockMvc.perform(get("/api/cards/" + a).session(session))
                .andExpect(jsonPath("$.columnId").value(to))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void 空の列にも移動できる() throws Exception {
        MockHttpSession session = newSession();
        List<BoardColumn> columns = columnsOf(session);
        Long from = columns.get(0).getId();
        Long empty = columns.get(2).getId();
        long a = addCard(session, from, "A");

        move(session, a, empty, null).andExpect(status().isNoContent());
        assertColumn(session, empty, "A");
        assertColumn(session, from);
    }

    @Test
    void 挿入位置のカードが移動先の列にない場合や自分自身は400() throws Exception {
        MockHttpSession session = newSession();
        List<BoardColumn> columns = columnsOf(session);
        Long col1 = columns.get(0).getId();
        Long col2 = columns.get(1).getId();
        long a = addCard(session, col1, "A");
        long b = addCard(session, col1, "B");

        // b は col1 にあるので、col2 への移動の挿入位置には指定できない
        move(session, a, col2, b).andExpect(status().isBadRequest());
        // 自分自身の手前は指定できない
        move(session, a, col1, a).andExpect(status().isBadRequest());
        move(session, a, null, null).andExpect(status().isBadRequest());
        assertColumn(session, col1, "A", "B");
    }

    @Test
    void 他人のカードや他人の列は動かせない() throws Exception {
        MockHttpSession owner = newSession();
        MockHttpSession other = newSession();
        Long ownerCol = columnsOf(owner).get(0).getId();
        Long otherCol = columnsOf(other).get(0).getId();
        long ownerCard = addCard(owner, ownerCol, "Aのカード");
        long otherCard = addCard(other, otherCol, "Bのカード");

        // 他人のカードを動かす
        move(other, ownerCard, otherCol, null).andExpect(status().isNotFound());
        // 自分のカードを他人の列へ動かす
        move(other, otherCard, ownerCol, null).andExpect(status().isNotFound());

        assertColumn(owner, ownerCol, "Aのカード");
        assertColumn(other, otherCol, "Bのカード");
    }

    @Test
    void 存在しないカードは404_未ログインは401_CSRFなしは403() throws Exception {
        MockHttpSession session = newSession();
        Long col = columnsOf(session).get(0).getId();
        long a = addCard(session, col, "A");

        move(session, 999999999L, col, null).andExpect(status().isNotFound());

        mockMvc.perform(
                        put("/api/cards/" + a + "/move")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"columnId\":%d}".formatted(col)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        put("/api/cards/" + a + "/move")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"columnId\":%d}".formatted(col)))
                .andExpect(status().isForbidden());
    }
}
