package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.CardMoveRequest;
import com.taskmanagement.backend.dto.CardRequest;
import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Label;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.exception.ResourceNotFoundException;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
import com.taskmanagement.backend.repository.LabelRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CardService {

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

    public List<CardResponse> search(
            Long userId, Long columnId, Priority priority, String keyword) {
        return cardRepository.search(userId, columnId, priority, escapeLike(keyword)).stream()
                .map(CardResponse::from)
                .toList();
    }

    /** 他の利用者のカードは存在しないものとして扱う(404)。 */
    public CardResponse findById(Long userId, Long id) {
        return CardResponse.from(findOwned(userId, id));
    }

    /** 指定カラムの末尾にカードを追加する。カラムが自分のものでなければ404。 */
    @Transactional
    public CardResponse create(Long userId, CardRequest request) {
        if (request.columnId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "カラムを指定してください");
        }
        BoardColumn column = findOwnedColumn(userId, request.columnId());

        Card card = new Card();
        card.setColumn(column);
        card.setPosition(cardRepository.maxPositionInColumn(column.getId()) + 1);
        apply(card, request);
        applyLabels(userId, card, request);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public CardResponse update(Long userId, Long id, CardRequest request) {
        Card card = findOwned(userId, id);
        apply(card, request);
        applyLabels(userId, card, request);
        return CardResponse.from(card);
    }

    /** 存在しない・他人のカードなら404。 */
    @Transactional
    public void delete(Long userId, Long id) {
        cardRepository.delete(findOwned(userId, id));
    }

    /** カードを移動先の列の指定位置へ移し、影響する列の position を 0 からの連番に振り直す。 カードが存在しない・他人のものなら404。 */
    @Transactional
    public void move(Long userId, Long id, CardMoveRequest request) {
        Card card = findOwned(userId, id);
        BoardColumn target = findOwnedColumn(userId, request.columnId());

        Long sourceColumnId = card.getColumn().getId();

        List<Card> ordered =
                new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(target.getId()));
        ordered.removeIf(c -> c.getId().equals(id));
        int index = ordered.size();
        if (request.beforeCardId() != null) {
            index = indexOfCard(ordered, request.beforeCardId());
            if (index < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "挿入位置のカードが移動先のカラムにありません");
            }
        }
        card.setColumn(target);
        ordered.add(index, card);
        renumber(ordered);

        if (!sourceColumnId.equals(target.getId())) {
            // 検索前に変更がフラッシュされ、移動したカードは移動元の結果から外れる
            renumber(cardRepository.findByColumnIdOrderByPosition(sourceColumnId));
        }
    }

    private Card findOwned(Long userId, Long id) {
        return cardRepository
                .findByIdAndColumnUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("カードが見つかりません"));
    }

    private BoardColumn findOwnedColumn(Long userId, Long columnId) {
        return boardColumnRepository
                .findByIdAndUserId(columnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("カラムが見つかりません"));
    }

    /** LIKE のワイルドカード(% _)と区切り文字(\)を、文字そのものとして検索するためにエスケープする。 */
    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
        card.setTitle(request.title());
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
        List<Label> labels =
                ids.isEmpty() ? List.of() : labelRepository.findByIdInAndUserId(ids, userId);
        if (labels.size() != ids.size()) {
            throw new ResourceNotFoundException("ラベルが見つかりません");
        }
        card.setLabels(new LinkedHashSet<>(labels));
    }
}
