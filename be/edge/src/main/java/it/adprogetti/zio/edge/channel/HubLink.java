package it.adprogetti.zio.edge.channel;

/** A live connection to the Hub, as seen by the rest of the Edge. */
public interface HubLink {

    /** @return true if the frame was written to the socket */
    boolean send(String frame);
}
