package org.irisadc.voiceotp.provider.api;

public class VoiceProviderException extends Exception {
    public enum Code {
        NOT_FOUND, ALREADY_EXISTS, VALIDATION_FAILED, VERSION_CONFLICT,
        SCOPE_FORBIDDEN, SECRET_STORE_UNAVAILABLE, ARI_UNREACHABLE,
        TEST_RATE_LIMITED, IN_USE, SERVICE_UNAVAILABLE
    }

    private final Code code;

    public VoiceProviderException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public VoiceProviderException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
