package io.mosip.registration.liveness.desktop.overlay;

/** A button the overlay offers. */
public enum Action {
    CANCEL("liveness.button.cancel"),
    RETRY("liveness.retry.button"),
    NEXT("liveness.button.next"),
    CLOSE("liveness.button.close"),
    RESCAN("liveness.button.rescan"),
    REQUEST_OVERRIDE("liveness.button.request_override"),
    MARK_EXCEPTION("liveness.button.mark_exception");

    private final String messageKey;

    Action(String messageKey) {
        this.messageKey = messageKey;
    }

    /** @return the button's label key */
    public String messageKey() {
        return messageKey;
    }
}
