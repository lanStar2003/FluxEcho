package com.fluxdepths.client;

import org.lwjgl.input.Keyboard;

/** Keyboard state for tooltips; only ever called on the client. */
public final class Keys {

    private Keys() {}

    public static boolean shift() {
        try {
            return Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        } catch (Throwable t) {
            return false;
        }
    }
}
