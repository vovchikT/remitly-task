package com.example.remitlytask;

class BadRequestException extends RuntimeException {
    BadRequestException(String message) {
        super(message);
    }
}
