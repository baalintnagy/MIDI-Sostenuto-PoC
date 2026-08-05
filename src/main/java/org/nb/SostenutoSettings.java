package org.nb;

record SostenutoSettings(
    int activationThreshold,
    int hysteresis,
    boolean umbrellaEnabled,
    int umbrellaDelayMs,
    int umbrellaTailMs
) {
}
