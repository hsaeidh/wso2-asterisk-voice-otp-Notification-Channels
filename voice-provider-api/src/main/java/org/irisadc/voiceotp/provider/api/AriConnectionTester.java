package org.irisadc.voiceotp.provider.api;

public interface AriConnectionTester {
    ProviderConnectionTestResult test(ProviderRecord provider, char[] secret)
            throws VoiceProviderException;
}
