package com.freshveggie.exception;

public class PasswordErrorException extends BaseException {

    public PasswordErrorException(String message) {
        super(message);
    }

    public PasswordErrorException() {
        super();
    }
}
