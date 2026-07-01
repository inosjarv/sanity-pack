package v2.model;

/** Outcome of a single step. SKIP means it never ran because an earlier step failed. */
public enum Status { PASS, FAIL, SKIP }
