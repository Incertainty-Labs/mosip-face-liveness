package io.mosip.registration.liveness.api;

/**
 * What the UI should show now: a message key from API.md §7, optional format arguments and a style level. Never
 * carries scores, cue names or other technical detail.
 */
public final class UserPrompt {

    private final String key;
    private final Object[] args;
    private final PromptLevel level;

    /**
     * @param key   the message key, for example {@code liveness.prompt.smile}
     * @param level how to style it
     * @param args  format arguments for the message, for example step numbers
     */
    public UserPrompt(String key, PromptLevel level, Object... args) {
        if (key == null || level == null) {
            throw new IllegalArgumentException("key and level must not be null");
        }
        this.key = key;
        this.level = level;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** @return the message key */
    public String key() {
        return key;
    }

    /** @return a copy of the format arguments; empty if none */
    public Object[] args() {
        return args.clone();
    }

    /** @return the style level */
    public PromptLevel level() {
        return level;
    }

    @Override
    public String toString() {
        return level + ":" + key;
    }
}
