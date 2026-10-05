package com.taskmanagement.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** カード検索(キーワード・優先度)と、入力検証エラー・作成時のLocationヘッダの確認。 */
@SpringBootTest
@AutoConfigureMockMvc
class CardSearchIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUsernames.forEach(
                name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
    }

    private record Login(MockHttpSession session, Long columnId) {}

    private Login newLogin() throws Exception {
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
        Long userId = userRepository.findByUsername(name).orElseThrow().getId();
        BoardColumn column = boardColumnRepository.findByUserIdOrderByPosition(userId).get(0);
        return new Login((MockHttpSession) result.getRequest().getSession(false), column.getId());
    }

    private void createCard(Login login, String title) throws Exception {
        mockMvc.perform(
                        post("/api/cards")
                                .session(login.session())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"columnId\":%d,\"title\":\"%s\"}"
                                                .formatted(login.columnId(), title)))
                .andExpect(status().isCreated());
    }

    @Test
    void キーワードは大文字小文字を区別せず部分一致で検索できる() throws Exception {
        Login login = newLogin();
        createCard(login, "Write Report");
        createCard(login, "買い物");

        mockMvc.perform(get("/api/cards").param("keyword", "report").session(login.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Write Report"));
    }

    @Test
    void キーワードのワイルドカード文字は文字そのものとして検索される() throws Exception {
        Login login = newLogin();
        createCard(login, "50% off");
        createCard(login, "snake_case");
        createCard(login, "plain");

        mockMvc.perform(get("/api/cards").param("keyword", "%").session(login.session()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("50% off"));
        mockMvc.perform(get("/api/cards").param("keyword", "_").session(login.session()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("snake_case"));
    }

    @Test
    void 入力エラーはProblemDetail形式の400で返る() throws Exception {
        Login login = newLogin();

        mockMvc.perform(
                        post("/api/cards")
                                .session(login.session())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"columnId\":%d,\"title\":\"\"}"
                                                .formatted(login.columnId())))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("タイトルは1〜50文字で入力してください"));
    }

    @Test
    void 作成のレスポンスにLocationヘッダが付く() throws Exception {
        Login login = newLogin();

        mockMvc.perform(
                        post("/api/cards")
                                .session(login.session())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"columnId\":%d,\"title\":\"x\"}"
                                                .formatted(login.columnId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }
}
