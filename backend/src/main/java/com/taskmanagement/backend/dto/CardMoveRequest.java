package com.taskmanagement.backend.dto;

import jakarta.validation.constraints.NotNull;

/** カードの移動先。beforeCardId のカードの手前に挿入し、null なら移動先の列の末尾に置く。 */
public record CardMoveRequest(
        @NotNull(message = "移動先のカラムを指定してください") Long columnId, Long beforeCardId) {}
