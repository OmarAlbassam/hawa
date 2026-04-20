package com.hawa.hawa_backend.reddit;

public class RedditServiceException extends RuntimeException {

    public RedditServiceException(String message) {
        super(message);
    }

    public RedditServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
