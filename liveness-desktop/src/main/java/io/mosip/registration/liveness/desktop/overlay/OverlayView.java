package io.mosip.registration.liveness.desktop.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the liveness overlay shows at one moment (UI_UX_SPEC §3.1), as message keys and numbers; the view turns
 * keys into text. Immutable.
 */
public final class OverlayView {

    private final UiState state;
    private final Level level;
    private final String statusKey;
    private final Object[] statusArgs;
    private final String promptKey;
    private final Cue cue;
    private final int stepTotal;
    private final float ring;
    private final float timeLeft;
    private final String toastKey;
    private final int toastSequence;
    private final List<String> guidanceKeys;
    private final int attempt;
    private final int maxAttempts;
    private final Set<Action> buttons;
    private final String errorCode;

    OverlayView(Builder b) {
        this.state = b.state;
        this.level = b.level;
        this.statusKey = b.statusKey;
        this.statusArgs = b.statusArgs.clone();
        this.promptKey = b.promptKey;
        this.cue = b.cue;
        this.stepTotal = b.stepTotal;
        this.ring = b.ring;
        this.timeLeft = b.timeLeft;
        this.toastKey = b.toastKey;
        this.toastSequence = b.toastSequence;
        this.guidanceKeys = Collections.unmodifiableList(new ArrayList<String>(b.guidanceKeys));
        this.attempt = b.attempt;
        this.maxAttempts = b.maxAttempts;
        this.buttons = Collections.unmodifiableSet(b.buttons.isEmpty() ? EnumSet.noneOf(Action.class)
                : EnumSet.copyOf(b.buttons));
        this.errorCode = b.errorCode;
    }

    public UiState state() {
        return state;
    }

    /** @return the colour and icon group */
    public Level level() {
        return level;
    }

    /** @return the status line's message key */
    public String statusKey() {
        return statusKey;
    }

    /** @return the status line's arguments, for example step 2 of 3 */
    public Object[] statusArgs() {
        return statusArgs.clone();
    }

    /** @return the prompt's message key, the largest text; null for none */
    public String promptKey() {
        return promptKey;
    }

    /** @return the icon beside the prompt */
    public Cue cue() {
        return cue;
    }

    /** @return the number of step dots; 0 hides them (the passive phase, or a neutral hold alone) */
    public int stepTotal() {
        return stepTotal;
    }

    /** @return the progress ring, 0..1 (passive frames gathered); NaN hides it */
    public float ring() {
        return ring;
    }

    /** @return the time bar, 1 when a challenge starts down to 0 at its timeout; NaN hides it */
    public float timeLeft() {
        return timeLeft;
    }

    /** @return the feedback toast's message key, shown for 1.5 s; null for none */
    public String toastKey() {
        return toastKey;
    }

    /** @return grows with each new toast, so a view can tell a repeated toast from the one already shown */
    public int toastSequence() {
        return toastSequence;
    }

    /** @return the guidance list shown after a liveness failure (S10); empty otherwise */
    public List<String> guidanceKeys() {
        return guidanceKeys;
    }

    /** @return the attempt just used, shown after a liveness or PAD failure; 0 hides the counter */
    public int attempt() {
        return attempt;
    }

    /** @return attempts allowed, beside {@link #attempt()}; 0 when hidden */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** @return the buttons offered */
    public Set<Action> buttons() {
        return buttons;
    }

    /** @return the error code (LIV-0xx) of a failure, for the audit and diagnostics; null otherwise */
    public String errorCode() {
        return errorCode;
    }

    static final class Builder {
        UiState state = UiState.IDLE;
        Level level = Level.INFO;
        String statusKey;
        Object[] statusArgs = new Object[0];
        String promptKey;
        Cue cue = Cue.NONE;
        int stepTotal;
        float ring = Float.NaN;
        float timeLeft = Float.NaN;
        String toastKey;
        int toastSequence;
        List<String> guidanceKeys = Collections.emptyList();
        int attempt;
        int maxAttempts;
        Set<Action> buttons = EnumSet.noneOf(Action.class);
        String errorCode;
    }
}
