package com.taskmanagement.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CardLabelIntegrationTest {

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
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password123\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static long idOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private long createLabel(MockHttpSession session, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/labels")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"color\":\"#00AA00\"}".formatted(name))));
    }

    private long firstColumnId(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(get("/api/auth/me").session(session))
                .andReturn().getResponse().getContentAsString();
        long userId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        return boardColumnRepository.findByUserIdOrderByPosition(userId).get(0).getId();
    }

    private ResultActions createCard(MockHttpSession session, long columnId, String labelIdsJson)
            throws Exception {
        String labels = labelIdsJson == null ? "" : ",\"labelIds\":" + labelIdsJson;
        return mockMvc.perform(post("/api/cards")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnId\":%d,\"title\":\"カード\"%s}".formatted(columnId, labels)));
    }

    private ResultActions updateCard(MockHttpSession session, long cardId, String labelIdsJson)
            throws Exception {
        String labels = labelIdsJson == null ? "" : ",\"labelIds\":" + labelIdsJson;
        return mockMvc.perform(put("/api/cards/" + cardId)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"更新\"%s}".formatted(labels)));
    }

    @Test
    void ラベルを指定してカードを作成できる() throws Exception {
        MockHttpSession session = newSession();
        long columnId = firstColumnId(session);
        long a = createLabel(session, "A");
        long b = createLabel(session, "B");

        createCard(session, columnId, "[%d,%d]".formatted(a, b))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labels", hasSize(2)))
                .andExpect(jsonPath("$.labels[0].name").value("A"))
                .andExpect(jsonPath("$.labels[1].name").value("B"));

        mockMvc.perform(get("/api/cards").session(session))
                .andExpect(jsonPath("$[0].labels", hasSize(2)));
    }

    @Test
    void labelIds省略ならラベルなしで作成され_更新で省略すると変更されない() throws Exception {
        MockHttpSession session = newSession();
        long columnId = firstColumnId(session);
        long a = createLabel(session, "A");

        long cardId = idOf(createCard(session, columnId, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labels", hasSize(0))));
        updateCard(session, cardId, "[%d]".formatted(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasSize(1)));
        updateCard(session, cardId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasSize(1)));
        updateCard(session, cardId, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasSize(0)));
    }

    @Test
    void 他人または存在しないラベルは404() throws Exception {
        MockHttpSession session = newSession();
        MockHttpSession other = newSession();
        long columnId = firstColumnId(session);
        long othersLabel = createLabel(other, "他人");

        createCard(session, columnId, "[%d]".formatted(othersLabel)).andExpect(status().isNotFound());
        createCard(session, columnId, "[999999999]").andExpect(status().isNotFound());
    }

    @Test
    void ラベルを削除すると紐付けも消えるがカードは残る() throws Exception {
        MockHttpSession session = newSession();
        long columnId = firstColumnId(session);
        long a = createLabel(session, "A");
        long cardId = idOf(createCard(session, columnId, "[%d]".formatted(a)));

        mockMvc.perform(delete("/api/labels/" + a).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/cards/" + cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasSize(0)));
    }

    @Test
    void カードを削除してもラベルは残る() throws Exception {
        MockHttpSession session = newSession();
        long columnId = firstColumnId(session);
        long a = createLabel(session, "A");
        long cardId = idOf(createCard(session, columnId, "[%d]".formatted(a)));

        mockMvc.perform(delete("/api/cards/" + cardId).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/labels").session(session)).andExpect(jsonPath("$", hasSize(1)));
    }
}
