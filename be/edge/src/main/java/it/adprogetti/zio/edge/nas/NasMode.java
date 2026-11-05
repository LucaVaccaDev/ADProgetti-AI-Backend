package it.adprogetti.zio.edge.nas;

/** The three ways the Edge may touch the NAS (capitolato 5.2). Each has its own allowed roots in the configuration. */
public enum NasMode {
    /** Reading for indexing: nothing can be modified or deleted. */
    INDEX_READ(false),
    /** Controlled writing for protocols: folders, communications, registers, cancellations. */
    PROTOCOL_WRITE(true),
    /** Controlled writing for tenders: new final folder after approval, approved documents; never historical tenders. */
    TENDER_WRITE(true);

    private final boolean writes;

    NasMode(boolean writes) {
        this.writes = writes;
    }

    public boolean writes() {
        return writes;
    }
}
