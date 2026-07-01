package v2.model;

/**
 * How much a failing check matters. CRITICAL failures fail the pack;
 * WARNING failures are surfaced in the report but do not block.
 */
public enum Severity { CRITICAL, WARNING }
