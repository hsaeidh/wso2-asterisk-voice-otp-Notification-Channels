package org.irisadc.voiceotp.provider.core;

import java.net.URI;

@FunctionalInterface
public interface AriUrlPolicy {
    void validate(URI uri);
}
