package io.mosip.registration.liveness.api;

/** The registration-client workflow a liveness session runs in. Each workflow can have its own policy. */
public enum Workflow {
    /** Resident face capture during a new registration, update, lost or correction. */
    RESIDENT_REGISTRATION("registration"),
    /**
     * The exception photo taken when a resident has biometric exceptions. Its mode defaults to PASSIVE, and only its
     * own key ({@code policy.exception_photo.mode}) changes it: the person may not be able to do the challenges.
     */
    RESIDENT_EXCEPTION_PHOTO("exception_photo"),
    /** Face capture while onboarding or updating an operator's biometrics. */
    OPERATOR_ONBOARDING("onboard_auth"),
    /** Operator login with face. */
    OPERATOR_LOGIN("login_auth"),
    /** Operator authentication before packet creation or upload. */
    OPERATOR_PACKET_AUTH("packet_auth"),
    /** Supervisor authentication for a registration with biometric exceptions. */
    SUPERVISOR_EXCEPTION_AUTH("exception_auth"),
    /** Supervisor authentication for end-of-day packet approval. */
    SUPERVISOR_EOD_AUTH("eod_auth");

    private final String policyCode;

    Workflow(String policyCode) {
        this.policyCode = policyCode;
    }

    /**
     * @return the code used in per-workflow config keys ({@code mosip.registration.face.liveness.policy.<code>.*});
     *         the Desktop {@code ProcessNames} codes plus {@code registration}
     */
    public String policyCode() {
        return policyCode;
    }
}
