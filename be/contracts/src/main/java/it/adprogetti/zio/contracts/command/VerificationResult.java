package it.adprogetti.zio.contracts.command;

public enum VerificationResult {
    OK,
    UNSUPPORTED_VERSION,
    BAD_SIGNATURE,
    EXPIRED,
    REPLAYED
}
