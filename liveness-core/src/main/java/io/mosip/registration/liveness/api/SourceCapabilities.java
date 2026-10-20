package io.mosip.registration.liveness.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Facts about a frame source: identity, certification level, resolution and frame rate. Immutable. */
public final class SourceCapabilities {

    private final String vendor;
    private final String model;
    private final String certification;
    private final int width;
    private final int height;
    private final double nominalFps;
    private final double measuredFps;
    private final boolean devicePad;
    private final Set<String> formats;

    private SourceCapabilities(Builder b) {
        this.vendor = b.vendor;
        this.model = b.model;
        this.certification = b.certification;
        this.width = b.width;
        this.height = b.height;
        this.nominalFps = b.nominalFps;
        this.measuredFps = b.measuredFps;
        this.devicePad = b.devicePad;
        this.formats = Collections.unmodifiableSet(new LinkedHashSet<String>(b.formats));
    }

    /** @return a builder with every field empty or zero */
    public static Builder builder() {
        return new Builder();
    }

    /** @return the device vendor, or an empty string if unknown */
    public String vendor() {
        return vendor;
    }

    /** @return the device model, or an empty string if unknown */
    public String model() {
        return model;
    }

    /** @return certification level: {@code L0}, {@code L1}, {@code L0-camera} or {@code MOCK} */
    public String certification() {
        return certification;
    }

    /** @return frame width in pixels, 0 if unknown */
    public int width() {
        return width;
    }

    /** @return frame height in pixels, 0 if unknown */
    public int height() {
        return height;
    }

    /** @return frame rate declared by the source, 0 if unknown */
    public double nominalFps() {
        return nominalFps;
    }

    /** @return frame rate measured over the last 2 s, 0 if not measured yet */
    public double measuredFps() {
        return measuredFps;
    }

    /** @return whether the device reports its own PAD verdict */
    public boolean devicePad() {
        return devicePad;
    }

    /** @return frame formats, for example {@code MJPEG}, {@code RGBA_8888}; unmodifiable */
    public Set<String> formats() {
        return formats;
    }

    /** Mutable builder for {@link SourceCapabilities}. Not thread-safe. */
    public static final class Builder {
        private String vendor = "";
        private String model = "";
        private String certification = "";
        private int width;
        private int height;
        private double nominalFps;
        private double measuredFps;
        private boolean devicePad;
        private Set<String> formats = Collections.emptySet();

        private Builder() {
        }

        /** @param vendorName device vendor @param modelName device model @return this builder */
        public Builder device(String vendorName, String modelName) {
            this.vendor = vendorName;
            this.model = modelName;
            return this;
        }

        /** @param level certification level @return this builder */
        public Builder certification(String level) {
            this.certification = level;
            return this;
        }

        /** @param w width in pixels @param h height in pixels @return this builder */
        public Builder resolution(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        /** @param nominal declared fps @param measured measured fps @return this builder */
        public Builder fps(double nominal, double measured) {
            this.nominalFps = nominal;
            this.measuredFps = measured;
            return this;
        }

        /** @param value whether the device reports PAD @return this builder */
        public Builder devicePad(boolean value) {
            this.devicePad = value;
            return this;
        }

        /** @param values supported formats; copied @return this builder */
        public Builder formats(Set<String> values) {
            this.formats = values;
            return this;
        }

        /** @return the immutable capabilities */
        public SourceCapabilities build() {
            return new SourceCapabilities(this);
        }
    }
}
