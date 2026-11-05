package it.adprogetti.zio.edge.channel;

/** Published each time the channel to the Hub opens, so modules can send their projections. */
public record HubConnectedEvent() {
}
