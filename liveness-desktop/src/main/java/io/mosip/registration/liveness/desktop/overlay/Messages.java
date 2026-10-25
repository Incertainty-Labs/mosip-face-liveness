package io.mosip.registration.liveness.desktop.overlay;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** The overlay's text by message key (API.md §7). Immutable. */
public final class Messages {

    private static final String BUNDLE = "io.mosip.registration.liveness.desktop.overlay.messages";

    private final ResourceBundle bundle;
    private final Locale locale;

    private Messages(ResourceBundle bundle, Locale locale) {
        this.bundle = bundle;
        this.locale = locale;
    }

    /** @return the English texts */
    public static Messages english() {
        return new Messages(ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH), Locale.ENGLISH);
    }

    /**
     * @param key the message key
     * @return whether it has text
     */
    public boolean has(String key) {
        return bundle.containsKey(key);
    }

    /**
     * @param key  the message key
     * @param args values for its {@code {0}}, {@code {1}} placeholders
     * @return the text, or the key itself when it has none, so a missing text is noticed
     */
    public String text(String key, Object... args) {
        if (!has(key)) {
            return key;
        }
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
