package com.taskmanagement.backend.exception;

/** 対象が存在しない、または他の利用者のものである場合に投げる(404になる)。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
