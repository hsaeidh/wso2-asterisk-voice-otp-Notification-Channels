package org.irisadc.voiceotp.provider.api;

import java.util.List;

public record Page<T>(List<T> items, int offset, int limit, long totalCount) {
    public Page {
        items = List.copyOf(items);
    }
}
