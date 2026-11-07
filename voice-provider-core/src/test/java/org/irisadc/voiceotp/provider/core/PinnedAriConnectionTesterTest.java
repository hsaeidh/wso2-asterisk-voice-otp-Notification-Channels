package org.irisadc.voiceotp.provider.core;

import org.irisadc.voiceotp.provider.api.ProviderRecord;
import org.irisadc.voiceotp.provider.api.VoiceProviderException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PinnedAriConnectionTesterTest {
    private final PinnedAriConnectionTester tester = new PinnedAriConnectionTester(
            Set.of("asterisk.example.test"), Set.of(443, 8089), 1000, 1000);

    @Test
    void rejectsUnapprovedHostBeforeDnsResolution() {
        VoiceProviderException error = assertThrows(VoiceProviderException.class,
                () -> tester.test(provider("https://attacker.example.test:8089/ari"), "secret".toCharArray()));
        assertEquals(VoiceProviderException.Code.VALIDATION_FAILED, error.getCode());
    }

    @Test
    void rejectsHttpAndPathConfusionBeforeNetworkAccess() {
        assertThrows(VoiceProviderException.class,
                () -> tester.test(provider("http://asterisk.example.test:8089/ari"), "secret".toCharArray()));
        assertThrows(VoiceProviderException.class,
                () -> tester.test(provider("https://asterisk.example.test:8089/ari-evil"), "secret".toCharArray()));
        assertThrows(VoiceProviderException.class,
                () -> tester.test(provider("https://asterisk.example.test:9443/ari"), "secret".toCharArray()));
    }

    private ProviderRecord provider(String url) {
        Instant now = Instant.now();
        return new ProviderRecord("provider-a", url, "ari-user", "secret-ref",
                "voiceotp", "PJSIP/100", "voiceotp", "s", "+10000000000",
                20, 60, "fa", true, 1, now, now);
    }
}
