package it.adprogetti.zio.edge.ai;

/** E-mail classes of the capitolato (sez. 6.3). The list may grow; the schema below is built from it. */
public enum EmailCategory {
    BUSINESS_RELEVANT, PROTOCOL_CANDIDATE, INTERNAL_ONLY, ADVERTISING, SPAM, POSSIBLE_PHISHING, UNCERTAIN,
    PEC_RECEIPT, IRRELEVANT
}
