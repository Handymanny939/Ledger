package com.ledger.model;

/**
 * Enum representing all valid transaction categories.
 * Using an enum instead of plain Strings gives us:
 *   - Compile-time safety (no typos)
 *   - A fixed, known set of values
 *   - Easy iteration (Category.values())
 *
 * displayName is used when printing to the user — cleaner than "FOOD".
 */
public enum Category {

    FOOD("Food & Dining"),
    RENT("Rent & Housing"),
    SALARY("Salary & Income"),
    TRANSPORT("Transport"),
    ENTERTAINMENT("Entertainment"),
    UTILITIES("Utilities"),
    OTHER("Other");

    // Each enum constant carries a human-readable label
    private final String displayName;

    // Enum constructors are always private — Java enforces this
    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Converts a stored String back to a Category enum constant.
     * Used when reading rows from the database.
     * Returns OTHER if the value is unrecognized — defensive programming.
     */
    public static Category fromString(String value) {
        for (Category c : values()) {
            if (c.name().equalsIgnoreCase(value)) {
                return c;
            }
        }
        return OTHER;
    }
}