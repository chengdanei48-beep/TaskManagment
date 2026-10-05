package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.LabelRequest;
import com.taskmanagement.backend.dto.LabelResponse;
import com.taskmanagement.backend.entity.Label;
import com.taskmanagement.backend.exception.ResourceNotFoundException;
import com.taskmanagement.backend.repository.LabelRepository;
import com.taskmanagement.backend.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class LabelService {

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

    /** ラベルを登録する。同じ名前が既にあれば409。名前・色の形式は DTO の Bean Validation で検証済み。 */
    @Transactional
    public LabelResponse create(Long userId, LabelRequest request) {
        if (labelRepository.existsByUserIdAndName(userId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同じ名前のラベルが既にあります");
        }
        Label label = new Label();
        label.setUser(userRepository.getReferenceById(userId));
        label.setName(request.name());
        label.setColor(request.color().toUpperCase());
        return LabelResponse.from(labelRepository.save(label));
    }

    /** 存在しない・他人のラベルなら404。 */
    @Transactional
    public void delete(Long userId, Long id) {
        Label label =
                labelRepository
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("ラベルが見つかりません"));
        labelRepository.delete(label);
    }
}
