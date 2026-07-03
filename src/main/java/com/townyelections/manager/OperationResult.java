package com.townyelections.manager;

/**
 * Lightweight result object returned by {@link ElectionManager} mutating
 * operations. Carries a success flag, a message key for the caller to display,
 * and an optional generic payload.
 */
public class OperationResult {

    private final boolean success;
    private final String messageKey;
    private final Object payload;

    private OperationResult(boolean success, String messageKey, Object payload) {
        this.success = success;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public static OperationResult ok(String messageKey) {
        return new OperationResult(true, messageKey, null);
    }

    public static OperationResult ok(String messageKey, Object payload) {
        return new OperationResult(true, messageKey, payload);
    }

    public static OperationResult fail(String messageKey) {
        return new OperationResult(false, messageKey, null);
    }

    public static OperationResult fail(String messageKey, Object payload) {
        return new OperationResult(false, messageKey, payload);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object getPayload() {
        return payload;
    }
}
