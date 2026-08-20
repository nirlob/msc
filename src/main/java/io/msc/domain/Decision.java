package io.msc.domain;

/**
 * Decision made about a contribution. Either automatically by the policy
 * engine, or by a human moderator.
 */
public enum Decision {
    AUTO_ACCEPT("auto_accept"),
    AUTO_REJECT("auto_reject"),
    ACCEPT("accept"),
    REJECT("reject"),
    EDIT("edit"),
    WITHDRAW("withdraw");

    private final String wire;

    Decision(String wire) { this.wire = wire; }

    public String wire() { return wire; }
}