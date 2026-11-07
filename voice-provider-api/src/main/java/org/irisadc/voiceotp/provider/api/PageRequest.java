package org.irisadc.voiceotp.provider.api;

public record PageRequest(int offset, int limit) {
    public PageRequest {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
    }

    public static PageRequest firstPage() {
        return new PageRequest(0, 20);
    }
}
