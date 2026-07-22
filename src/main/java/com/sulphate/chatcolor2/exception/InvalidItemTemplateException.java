package com.sulphate.chatcolor2.exception;

import java.io.Serial;

public class InvalidItemTemplateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidItemTemplateException(String message) {
        super(message);
    }

}
