package io.mosip.registration.liveness.api;

/** Who and what a liveness session is for. Immutable; build it with {@link #builder()}. */
public final class LivenessContext {

    private final Workflow workflow;
    private final String refId;
    private final String transactionId;
    private final int attempt;
    private final SourceCapabilities source;

    private LivenessContext(Builder b) {
        this.workflow = b.workflow;
        this.refId = b.refId;
        this.transactionId = b.transactionId;
        this.attempt = b.attempt;
        this.source = b.source;
    }

    /** @return a new builder; {@code workflow} is required */
    public static Builder builder() {
        return new Builder();
    }

    /** @return the workflow */
    public Workflow workflow() {
        return workflow;
    }

    /** @return the registration ID during registration, or the user ID during authentication */
    public String refId() {
        return refId;
    }

    /** @return the SBI capture transaction this session belongs to, or an empty string */
    public String transactionId() {
        return transactionId;
    }

    /** @return the 1-based attempt number */
    public int attempt() {
        return attempt;
    }

    /** @return capabilities of the frame source feeding this session */
    public SourceCapabilities source() {
        return source;
    }

    /** Mutable builder for {@link LivenessContext}. Not thread-safe. */
    public static final class Builder {
        private Workflow workflow;
        private String refId = "";
        private String transactionId = "";
        private int attempt = 1;
        private SourceCapabilities source = SourceCapabilities.builder().build();

        private Builder() {
        }

        /** @param value the workflow @return this builder */
        public Builder workflow(Workflow value) {
            this.workflow = value;
            return this;
        }

        /** @param value registration ID or user ID @return this builder */
        public Builder refId(String value) {
            this.refId = value;
            return this;
        }

        /** @param value SBI capture transaction ID @return this builder */
        public Builder transactionId(String value) {
            this.transactionId = value;
            return this;
        }

        /** @param value 1-based attempt number @return this builder */
        public Builder attempt(int value) {
            this.attempt = value;
            return this;
        }

        /** @param value capabilities of the frame source @return this builder */
        public Builder source(SourceCapabilities value) {
            this.source = value;
            return this;
        }

        /**
         * @return the immutable context
         * @throws IllegalArgumentException if the workflow is missing or the attempt is below 1
         */
        public LivenessContext build() {
            if (workflow == null || attempt < 1) {
                throw new IllegalArgumentException("workflow is required and attempt must be 1 or more");
            }
            return new LivenessContext(this);
        }
    }
}
