package com.sulphate.chatcolor2.exception;

import java.io.Serial;

public class InvalidGuiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidGuiException(String reason) {
        super(reason);
    }

}
