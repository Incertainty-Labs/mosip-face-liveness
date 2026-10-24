package io.mosip.registration.liveness.engine;

import io.mosip.registration.liveness.api.Challenge;
import io.mosip.registration.liveness.api.ChallengeProgress;
import io.mosip.registration.liveness.api.ChallengeType;
import io.mosip.registration.liveness.api.ChallengeVerifier;
import io.mosip.registration.liveness.api.FaceObservation;

/**
 * The verifiers of TECHNICAL_DESIGN §7.2, judged against the user's own {@link Baseline}. A verifier is for one
 * challenge of one session; it is not thread-safe.
 */
public final class ChallengeVerifiers {

    /** Turn: yaw at least this far from the baseline. */
    static final double TURN_DEG = 20;
    /** Start condition of a pose challenge: within this of the baseline on the tested axis. */
    static final double START_DEG = 10;
    /** Look up or down: pitch at least this far from the baseline (a real look up measured 29°). */
    static final double LOOK_DEG = 15;
    /** Hold for the pose challenges and OPEN_MOUTH. */
    static final long POSE_HOLD_MS = 300;
    /** SMILE: the smile score at least this far above the baseline (a real smile measured 0.80 over 0.00). */
    static final double SMILE_RISE = 0.35;
    /** SMILE start condition: no more than this above the baseline. */
    static final double SMILE_START = 0.15;
    /** SMILE: the mouth must stay below this, since an open mouth also reads as a smile. */
    static final double SMILE_MAX_MOUTH = 0.25;
    /** Hold for SMILE. */
    static final long SMILE_HOLD_MS = 400;
    /** OPEN_MOUTH: mouth openness at least this (a real open mouth measured 0.43 to 0.54). */
    static final double MOUTH_OPEN = 0.35;
    /** OPEN_MOUTH start condition: mouth openness below this. */
    static final double MOUTH_CLOSED = 0.2;
    /** Eyes count as open at or above this (open eyes measured 0.66 to 0.97). */
    static final double EYES_OPEN = 0.6;
    /** An eye counts as closed at or below this (closed eyes measured 0.22 to 0.47, unevenly between the eyes). */
    static final double EYES_CLOSED = 0.55;
    /** Hold for CLOSE_EYES. */
    static final long CLOSE_EYES_HOLD_MS = 700;
    /** BLINK: the eyes must open again within this of closing. */
    static final long BLINK_WINDOW_MS = 600;
    /** BLINK: closed for at least this many frames. */
    static final int BLINK_MIN_CLOSED_FRAMES = 2;
    /** NEUTRAL_HOLD: yaw, pitch and roll within this of the baseline. */
    static final double NEUTRAL_DEG = 5;
    /** Hold for NEUTRAL_HOLD. */
    static final long NEUTRAL_HOLD_MS = 1000;

    private ChallengeVerifiers() {
    }

    /**
     * @param type     the challenge
     * @param baseline the user's neutral face
     * @return a fresh verifier for one challenge
     */
    public static ChallengeVerifier create(ChallengeType type, Baseline baseline) {
        return type == ChallengeType.BLINK ? new BlinkVerifier() : new HoldVerifier(type, rule(type, baseline));
    }

    /**
     * BLINK: eyes open, then closed for at least {@value #BLINK_MIN_CLOSED_FRAMES} frames, then open again within
     * {@value #BLINK_WINDOW_MS} ms of closing. One closed frame may be noise; eyes kept closed longer are not a blink.
     */
    private static final class BlinkVerifier implements ChallengeVerifier {
        private long startedMs = -1;
        private boolean open;
        private long closedSinceMs = -1;
        private int closedFrames;

        @Override
        public ChallengeType type() {
            return ChallengeType.BLINK;
        }

        @Override
        public ChallengeProgress update(Challenge challenge, FaceObservation o, long timestampMs) {
            if (startedMs < 0) {
                startedMs = timestampMs;
            }
            if (timestampMs - startedMs >= challenge.timeoutMs()) {
                return ChallengeProgress.TIMED_OUT;
            }
            boolean eyesOpen = o.eyeOpenLeft() >= EYES_OPEN && o.eyeOpenRight() >= EYES_OPEN;
            boolean eyesClosed = o.eyeOpenLeft() <= EYES_CLOSED && o.eyeOpenRight() <= EYES_CLOSED;
            if (eyesClosed && open) {
                if (closedSinceMs < 0) {
                    closedSinceMs = timestampMs;
                }
                closedFrames++;
            } else if (eyesOpen) {
                boolean blinked = closedSinceMs >= 0 && closedFrames >= BLINK_MIN_CLOSED_FRAMES
                        && timestampMs - closedSinceMs <= BLINK_WINDOW_MS;
                if (blinked) {
                    return ChallengeProgress.PASSED;
                }
                open = true;
                closedSinceMs = -1;
                closedFrames = 0;
            }
            return ChallengeProgress.IN_PROGRESS;
        }

        @Override
        public void reset() {
            startedMs = -1;
            open = false;
            closedSinceMs = -1;
            closedFrames = 0;
        }
    }

    private static Rule rule(ChallengeType type, final Baseline b) {
        switch (type) {
            case TURN_LEFT:
                return new PoseRule(true, TURN_DEG, b);
            case TURN_RIGHT:
                return new PoseRule(true, -TURN_DEG, b);
            case LOOK_UP:
                return new PoseRule(false, LOOK_DEG, b);
            case LOOK_DOWN:
                return new PoseRule(false, -LOOK_DEG, b);
            case SMILE:
                return new Rule(SMILE_HOLD_MS) {
                    @Override
                    boolean ready(FaceObservation o) {
                        return o.smile() - b.smile() <= SMILE_START;
                    }

                    @Override
                    boolean done(FaceObservation o) {
                        // An open mouth also raises the smile score (0.59 measured), so the mouth must stay closed
                        return o.smile() - b.smile() >= SMILE_RISE && o.mouthOpen() < SMILE_MAX_MOUTH;
                    }
                };
            case OPEN_MOUTH:
                return new Rule(POSE_HOLD_MS) {
                    @Override
                    boolean ready(FaceObservation o) {
                        return o.mouthOpen() < MOUTH_CLOSED;
                    }

                    @Override
                    boolean done(FaceObservation o) {
                        return o.mouthOpen() >= MOUTH_OPEN;
                    }
                };
            case CLOSE_EYES:
                return new Rule(CLOSE_EYES_HOLD_MS) {
                    @Override
                    boolean ready(FaceObservation o) {
                        return o.eyeOpenLeft() >= EYES_OPEN && o.eyeOpenRight() >= EYES_OPEN;
                    }

                    @Override
                    boolean done(FaceObservation o) {
                        return o.eyeOpenLeft() <= EYES_CLOSED && o.eyeOpenRight() <= EYES_CLOSED;
                    }
                };
            case NEUTRAL_HOLD:
                return new Rule(NEUTRAL_HOLD_MS) {
                    @Override
                    boolean ready(FaceObservation o) {
                        return true;
                    }

                    @Override
                    boolean done(FaceObservation o) {
                        return Math.abs(o.yawDeg() - b.yawDeg()) <= NEUTRAL_DEG
                                && Math.abs(o.pitchDeg() - b.pitchDeg()) <= NEUTRAL_DEG
                                && Math.abs(o.rollDeg() - b.rollDeg()) <= NEUTRAL_DEG
                                && o.eyeOpenLeft() >= EYES_OPEN && o.eyeOpenRight() >= EYES_OPEN
                                && o.mouthOpen() < MOUTH_CLOSED;
                    }
                };
            default:
                throw new IllegalArgumentException("No verifier for " + type);
        }
    }

    /** A head turn (yaw) or nod (pitch) of at least {@code delta} degrees from the baseline, in its sign. */
    private static final class PoseRule extends Rule {
        private final boolean yaw;
        private final double delta;
        private final Baseline b;

        PoseRule(boolean yaw, double delta, Baseline b) {
            super(POSE_HOLD_MS);
            this.yaw = yaw;
            this.delta = delta;
            this.b = b;
        }

        private double offset(FaceObservation o) {
            return yaw ? o.yawDeg() - b.yawDeg() : o.pitchDeg() - b.pitchDeg();
        }

        @Override
        boolean ready(FaceObservation o) {
            return Math.abs(offset(o)) <= START_DEG;
        }

        @Override
        boolean done(FaceObservation o) {
            return delta > 0 ? offset(o) >= delta : offset(o) <= delta;
        }
    }

    /** What one challenge asks for: where the face must start, what it must do, and for how long. */
    private abstract static class Rule {
        final long holdMs;

        Rule(long holdMs) {
            this.holdMs = holdMs;
        }

        /** @return whether the face is in the challenge's start position */
        abstract boolean ready(FaceObservation o);

        /** @return whether the face does what the challenge asks */
        abstract boolean done(FaceObservation o);
    }

    /**
     * Waits for the start position, then passes once the action has held for the rule's hold time; times out at
     * {@link Challenge#timeoutMs()} from its first frame.
     */
    private static final class HoldVerifier implements ChallengeVerifier {
        private final ChallengeType type;
        private final Rule rule;
        private long startedMs = -1;
        private boolean ready;
        private long holdSinceMs = -1;

        HoldVerifier(ChallengeType type, Rule rule) {
            this.type = type;
            this.rule = rule;
        }

        @Override
        public ChallengeType type() {
            return type;
        }

        @Override
        public ChallengeProgress update(Challenge challenge, FaceObservation observation, long timestampMs) {
            if (startedMs < 0) {
                startedMs = timestampMs;
            }
            if (timestampMs - startedMs >= challenge.timeoutMs()) {
                return ChallengeProgress.TIMED_OUT;
            }
            if (!ready) {
                // A pose still held from the previous challenge must not count: wait for the start position
                ready = rule.ready(observation);
                if (!ready) {
                    return ChallengeProgress.IN_PROGRESS;
                }
            }
            if (rule.done(observation)) {
                if (holdSinceMs < 0) {
                    holdSinceMs = timestampMs;
                }
                if (timestampMs - holdSinceMs >= rule.holdMs) {
                    return ChallengeProgress.PASSED;
                }
            } else {
                holdSinceMs = -1;
            }
            return ChallengeProgress.IN_PROGRESS;
        }

        @Override
        public void reset() {
            startedMs = -1;
            ready = false;
            holdSinceMs = -1;
        }
    }
}
