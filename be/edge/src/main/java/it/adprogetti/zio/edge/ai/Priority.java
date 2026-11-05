package it.adprogetti.zio.edge.ai;

/** Order in the AI queue. Night batch work (re-indexing, archive analysis) uses LOW. */
public enum Priority {
    HIGH, NORMAL, LOW
}
