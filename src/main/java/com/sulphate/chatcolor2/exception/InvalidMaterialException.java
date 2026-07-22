package com.sulphate.chatcolor2.exception;

import java.io.Serial;

public class InvalidMaterialException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMaterialException(String invalidName) {
        super("Invalid material: " + invalidName);
    }

}
