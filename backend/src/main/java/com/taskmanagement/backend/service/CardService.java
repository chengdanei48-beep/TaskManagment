package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.CardMoveRequest;
import com.taskmanagement.backend.dto.CardRequest;
import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Label;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
import com.taskmanagement.backend.repository.LabelRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardService {

    static final int TITLE_MAX = 50;
    static final int DESCRIPTION_MAX = 500;

    private final CardRepository cardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final LabelRepository labelRepository;

    public CardService(
            CardRepository cardRepository,
            BoardColumnRepository boardColumnRepository,
            LabelRepository labelRepository) {
        this.cardRepository = cardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.labelRepository = labelRepository;
    }

    public List<CardResponse> search(Long userId, Long columnId, Priority priority, String keyword) {
        return cardRepository.search(userId, columnId, priority, keyword).stream()
                .map(CardResponse::from)
                .toList();
    }

    /** 他の利用者のカードは存在しないものとして扱う。 */
    public Optional<CardResponse> findById(Long userId, Long id) {
        return cardRepository.findByIdAndColumnUserId(id, userId).map(CardResponse::from);
    }

    /** 指定カラムの末尾にカードを追加する。カラムが自分のものでなければ404。 */
    @Transactional
    public CardResponse create(Long userId, CardRequest request) {
        validate(request);
        if (request.columnId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "カラムを指定してください");
        }
        BoardColumn column = boardColumnRepository
                .findByIdAndUserId(request.columnId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カラムが見つかりません"));

        Card card = new Card();
        card.setColumn(column);
        card.setPosition(cardRepository.maxPositionInColumn(column.getId()) + 1);
        apply(card, request);
        applyLabels(userId, card, request);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public Optional<CardResponse> update(Long userId, Long id, CardRequest request) {
        validate(request);
        return cardRepository.findByIdAndColumnUserId(id, userId).map(card -> {
            apply(card, request);
            applyLabels(userId, card, request);
            return CardResponse.from(card);
        });
    }

    /** 削除できたら true。存在しない・他人のカードなら false。 */
    @Transactional
    public boolean delete(Long userId, Long id) {
        return cardRepository.findByIdAndColumnUserId(id, userId)
                .map(card -> {
                    cardRepository.delete(card);
                    return true;
                })
                .orElse(false);
    }

    /**
     * カードを移動先の列の指定位置へ移し、影響する列の position を 0 からの連番に振り直す。
     * カードが存在しない・他人のものなら false。
     */
    @Transactional
    public boolean move(Long userId, Long id, CardMoveRequest request) {
        if (request.columnId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "移動先のカラムを指定してください");
        }
        Optional<Card> found = cardRepository.findByIdAndColumnUserId(id, userId);
        if (found.isEmpty()) {
            return false;
        }
        BoardColumn target = boardColumnRepository
                .findByIdAndUserId(request.columnId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カラムが見つかりません"));

        Card card = found.get();
        Long sourceColumnId = card.getColumn().getId();

        List<Card> ordered = new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(target.getId()));
        ordered.removeIf(c -> c.getId().equals(id));
        int index = ordered.size();
        if (request.beforeCardId() != null) {
            index = indexOfCard(ordered, request.beforeCardId());
            if (index < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "挿入位置のカードが移動先のカラムにありません");
            }
        }
        card.setColumn(target);
        ordered.add(index, card);
        renumber(ordered);

        if (!sourceColumnId.equals(target.getId())) {
            // 検索前に変更がフラッシュされ、移動したカードは移動元の結果から外れる
            renumber(cardRepository.findByColumnIdOrderByPosition(sourceColumnId));
        }
        return true;
    }

    private static int indexOfCard(List<Card> cards, Long cardId) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getId().equals(cardId)) {
                return i;
            }
        }
        return -1;
    }

    private static void renumber(List<Card> cards) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setPosition(i);
        }
    }

    private static void apply(Card card, CardRequest request) {
        card.setTitle(request.title().trim());
        String description = request.description();
        card.setDescription(description == null || description.isBlank() ? null : description);
        card.setDueDate(request.dueDate());
        card.setPriority(request.priority());
    }

    /** labelIds が null なら何もしない。指定されたら置き換える。自分のものでないラベルがあれば404。 */
    private void applyLabels(Long userId, Card card, CardRequest request) {
        if (request.labelIds() == null) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(request.labelIds());
        List<Label> labels = ids.isEmpty() ? List.of() : labelRepository.findByIdInAndUserId(ids, userId);
        if (labels.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ラベルが見つかりません");
        }
        card.setLabels(new LinkedHashSet<>(labels));
    }

    private static void validate(CardRequest request) {
        String title = request.title();
        if (title == null || title.isBlank() || title.trim().length() > TITLE_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "タイトルは1〜" + TITLE_MAX + "文字で入力してください");
        }
        if (request.description() != null && request.description().length() > DESCRIPTION_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "詳細説明は" + DESCRIPTION_MAX + "文字までで入力してください");
        }
    }
}
