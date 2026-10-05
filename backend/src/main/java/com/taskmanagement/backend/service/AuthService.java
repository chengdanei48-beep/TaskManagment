package com.taskmanagement.backend.service;

import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.User;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.UserRepository;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    static final int USERNAME_MAX = 50;
    static final int PASSWORD_MIN = 8;
    // BCryptは72バイトを超える部分を無視するため上限を設ける
    static final int PASSWORD_MAX = 72;
    private static final String UNIQUE_VIOLATION = "23505";
    private static final List<String> DEFAULT_COLUMNS = List.of("未着手", "進行中", "完了");

    private final UserRepository userRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            BoardColumnRepository boardColumnRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 利用者を登録し、初期カラム(未着手・進行中・完了)を作成する。 */
    @Transactional
    public User register(String username, String password) {
        validate(username, password);
        if (userRepository.existsByUsername(username)) {
            throw duplicated();
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // existsByUsername との間に同名登録が割り込んだ場合のみ重複扱いにする
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UNIQUE_VIOLATION.equals(cve.getSQLState())) {
                throw duplicated();
            }
            throw e;
        }
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            BoardColumn column = new BoardColumn();
            column.setUser(user);
            column.setName(DEFAULT_COLUMNS.get(i));
            column.setPosition(i);
            boardColumnRepository.save(column);
        }
        return user;
    }

    private static void validate(String username, String password) {
        if (username == null || username.isBlank() || username.length() > USERNAME_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "ユーザー名は1〜" + USERNAME_MAX + "文字で入力してください");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "パスワードは" + PASSWORD_MIN + "〜" + PASSWORD_MAX + "文字で入力してください");
        }
    }

    private static ResponseStatusException duplicated() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "このユーザー名は既に使われています");
    }
}
