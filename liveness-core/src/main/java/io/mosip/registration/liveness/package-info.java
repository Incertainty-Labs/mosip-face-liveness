/**
 * Shared face liveness and presentation attack detection engine for the MOSIP Registration Clients.
 *
 * <p>This module runs unchanged on the Desktop client (Java 21) and the Android client (API 28), so it is
 * compiled with {@code --release 8} and uses no AWT, JavaFX or {@code android.*} types. The only third-party
 * dependency is the ONNX Runtime API, supplied by the host application. The contract is documented in
 * {@code docs/design/API.md} §3.
 */
package io.mosip.registration.liveness;
