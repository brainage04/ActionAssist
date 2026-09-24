package io.github.brainage04.actionassist.core;

/** The purpose-built automation routines. */
public enum Macro {
    /**
     * Sneaks and right-clicks dirt with an empty hand at 20 clicks per second, keeps the hand empty
     * by moving the hotbar into the main inventory, compacts pebbles, and deposits when full.
     */
    PEBBLE,
    /**
     * Right-clicks at 20 clicks per second, taps sneak on every client tick, holds the vein-mining
     * key, and deposits when full.
     */
    CROP
}
