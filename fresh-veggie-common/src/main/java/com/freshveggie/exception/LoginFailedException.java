package com.freshveggie.exception;

public class LoginFailedException extends BaseException {
    public LoginFailedException(String message) {
        super(message);
    }

    public LoginFailedException() {

    }
}
