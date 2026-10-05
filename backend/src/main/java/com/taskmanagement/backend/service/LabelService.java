package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.LabelRequest;
import com.taskmanagement.backend.dto.LabelResponse;
import com.taskmanagement.backend.entity.Label;
import com.taskmanagement.backend.repository.LabelRepository;
import com.taskmanagement.backend.repository.UserRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabelService {

    static final int NAME_MAX = 20;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final LabelRepository labelRepository;
    private final UserRepository userRepository;

    public LabelService(LabelRepository labelRepository, UserRepository userRepository) {
        this.labelRepository = labelRepository;
        this.userRepository = userRepository;
    }

    public List<LabelResponse> findAll(Long userId) {
        return labelRepository.findByUserIdOrderById(userId).stream()
                .map(LabelResponse::from)
                .toList();
    }

    /** ラベルを登録する。名前は1〜20文字、色は #RRGGBB。同じ名前が既にあれば409。 */
    @Transactional
    public LabelResponse create(Long userId, LabelRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "ラベル名は1〜" + NAME_MAX + "文字で入力してください");
        }
        String color = request.color();
        if (color == null || !COLOR.matcher(color).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "色は #RRGGBB 形式で指定してください");
        }
        if (labelRepository.existsByUserIdAndName(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同じ名前のラベルが既にあります");
        }
        Label label = new Label();
        label.setUser(userRepository.getReferenceById(userId));
        label.setName(name);
        label.setColor(color.toUpperCase());
        return LabelResponse.from(labelRepository.save(label));
    }

    /** 削除できたら true。存在しない・他人のラベルなら false。 */
    @Transactional
    public boolean delete(Long userId, Long id) {
        return labelRepository.findByIdAndUserId(id, userId)
                .map(label -> {
                    labelRepository.delete(label);
                    return true;
                })
                .orElse(false);
    }
}
