package it.adprogetti.zio.contracts.command;

/** A command with the Ed25519 signature (Base64) computed by the Hub. */
public record SignedCommand(HubCommand command, String signature) {
}
