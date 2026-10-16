package com.freshveggie.exception;

public class DeletionNotAllowedException extends BaseException {
    public DeletionNotAllowedException(String message) {
        super(message);
    }
}
