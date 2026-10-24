package io.mosip.registration.liveness.model;

import io.mosip.registration.liveness.api.LivenessErrorCode;
import io.mosip.registration.liveness.api.LivenessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model bundle (API.md §14): {@code models.json} and the ONNX files it lists, each checked against its SHA-256
 * before anything runs; any problem fails closed with LIV-013 (TC-ENG-19). Immutable.
 */
public final class ModelRegistry {

    /** The manifest's file name in a model folder. */
    public static final String MANIFEST = "models.json";

    private final String bundleVersion;
    private final Map<String, String> versions;
    private final Map<String, byte[]> bytes;

    private ModelRegistry(String bundleVersion, Map<String, String> versions, Map<String, byte[]> bytes) {
        this.bundleVersion = bundleVersion;
        this.versions = Collections.unmodifiableMap(versions);
        this.bytes = bytes;
    }

    /**
     * Desktop: loads {@code models.json} and its files from a folder.
     *
     * @param modelDir the folder
     * @return the registry
     * @throws LivenessException LIV-013 if the manifest or a file cannot be read or is invalid
     */
    public static ModelRegistry load(Path modelDir) throws LivenessException {
        String manifest;
        try {
            manifest = new String(Files.readAllBytes(modelDir.resolve(MANIFEST)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw failure("Cannot read " + modelDir.resolve(MANIFEST));
        }
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        for (Map<String, Object> model : models(manifest)) {
            String file = text(model, "file");
            try {
                files.put(file, Files.readAllBytes(modelDir.resolve(file)));
            } catch (IOException e) {
                throw failure("Cannot read model file " + file);
            }
        }
        return load(files, manifest);
    }

    /**
     * Android: from bytes already read from the assets.
     *
     * @param files        file name to its bytes
     * @param manifestJson the {@code models.json} text
     * @return the registry
     * @throws LivenessException LIV-013 if the manifest is invalid or a file is missing
     */
    public static ModelRegistry load(Map<String, byte[]> files, String manifestJson) throws LivenessException {
        Map<String, Object> root = root(manifestJson);
        Map<String, String> versions = new LinkedHashMap<String, String>();
        Map<String, byte[]> bytes = new HashMap<String, byte[]>();
        for (Map<String, Object> model : models(manifestJson)) {
            String name = text(model, "name");
            byte[] data = files.get(text(model, "file"));
            if (data == null) {
                throw failure("Model file missing: " + text(model, "file"));
            }
            // Fail closed: a model that is not exactly the one in the manifest never runs
            if (!sha256(data).equalsIgnoreCase(text(model, "sha256"))) {
                throw failure("Model file does not match its SHA-256 in " + MANIFEST + ": " + text(model, "file"));
            }
            versions.put(name, text(model, "version"));
            bytes.put(name, data);
        }
        return new ModelRegistry(text(root, "bundleVersion"), versions, bytes);
    }

    /** @return the bundle version from the manifest */
    public String bundleVersion() {
        return bundleVersion;
    }

    /** @return model name to version, in manifest order; unmodifiable */
    public Map<String, String> versions() {
        return versions;
    }

    /**
     * @param name a model name from the manifest, for example {@code yunet}
     * @return the model file's bytes, verified against the manifest
     * @throws IllegalArgumentException if the bundle has no such model
     */
    public byte[] bytes(String name) {
        byte[] data = bytes.get(name);
        if (data == null) {
            throw new IllegalArgumentException("No model " + name + " in the bundle");
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String manifestJson) throws LivenessException {
        try {
            return (Map<String, Object>) Json.parse(manifestJson);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw failure("Invalid " + MANIFEST + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> models(String manifestJson) throws LivenessException {
        try {
            return (List<Map<String, Object>>) root(manifestJson).get("models");
        } catch (ClassCastException e) {
            throw failure("Invalid " + MANIFEST + ": models is not a list");
        }
    }

    private static String text(Map<String, Object> map, String key) throws LivenessException {
        Object value = map.get(key);
        if (!(value instanceof String)) {
            throw failure("Invalid " + MANIFEST + ": no " + key);
        }
        return (String) value;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    private static LivenessException failure(String detail) {
        return new LivenessException(LivenessErrorCode.ENGINE_ERROR, detail);
    }
}
