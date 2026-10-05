package com.taskmanagement.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Priority;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardColumnRepository boardColumnRepository;
    @Autowired CardRepository cardRepository;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // users 削除で columns / cards も ON DELETE CASCADE で消える
        createdUsernames.forEach(
                name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
    }

    private String newUsername() {
        String name = "test_" + UUID.randomUUID().toString().substring(0, 8);
        createdUsernames.add(name);
        return name;
    }

    private static String json(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    private MvcResult register(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void 未ログインで保護APIにアクセスすると401() throws Exception {
        mockMvc.perform(get("/api/cards")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/columns")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void ヘルスチェックは未ログインでも見られる() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void 登録すると初期の3列が作られ_そのままログイン状態になる() throws Exception {
        MockHttpSession session = sessionOf(register(newUsername()));

        mockMvc.perform(get("/api/columns").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("未着手"))
                .andExpect(jsonPath("$[1].name").value("進行中"))
                .andExpect(jsonPath("$[2].name").value("完了"));
    }

    @Test
    void パスワードはハッシュ化されて保存される() throws Exception {
        String username = newUsername();
        register(username);

        String hash = userRepository.findByUsername(username).orElseThrow().getPasswordHash();
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, hash);
        org.junit.jupiter.api.Assertions.assertTrue(hash.startsWith("$2"));
    }

    @Test
    void ユーザー名が重複すると409() throws Exception {
        String username = newUsername();
        register(username);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void 短すぎるパスワードと空のユーザー名は400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newUsername(), "short")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(" ", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ログインできる_パスワード違いは401() throws Exception {
        String username = newUsername();
        register(username);

        MvcResult ok = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        mockMvc.perform(get("/api/auth/me").session(sessionOf(ok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "wrong-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("no_such_user", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ログアウトすると再びログインが必要になる() throws Exception {
        MockHttpSession session = sessionOf(register(newUsername()));
        mockMvc.perform(get("/api/cards").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cards").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void CSRFトークンなしのPOSTは403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("x", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 他の利用者のカードは一覧にも個別取得にも出ない() throws Exception {
        MockHttpSession sessionA = sessionOf(register(newUsername()));
        MockHttpSession sessionB = sessionOf(register(newUsername()));

        Long userIdA = userIdOf(sessionA);
        BoardColumn columnA = boardColumnRepository.findByUserIdOrderByPosition(userIdA).get(0);
        Card card = new Card();
        card.setColumn(columnA);
        card.setTitle("Aだけのカード");
        card.setPriority(Priority.HIGH);
        card.setPosition(0);
        Long cardId = cardRepository.save(card).getId();

        mockMvc.perform(get("/api/cards").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Aだけのカード"));
        mockMvc.perform(get("/api/cards/" + cardId).session(sessionA)).andExpect(status().isOk());

        mockMvc.perform(get("/api/cards").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/cards/" + cardId).session(sessionB))
                .andExpect(status().isNotFound());
        // 他人のカラムIDで絞り込んでも見えない
        mockMvc.perform(get("/api/cards?columnId=" + columnA.getId()).session(sessionB))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/columns").session(sessionB))
                .andExpect(jsonPath("$[*].id", not(org.hamcrest.Matchers.hasItem(columnA.getId().intValue()))));
    }

    @Test
    void エラー応答にパスワードを含めない() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("no_such_user", "secret-password-xyz")))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("secret-password-xyz"))));
    }

    private Long userIdOf(MockHttpSession session) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String body = me.getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
