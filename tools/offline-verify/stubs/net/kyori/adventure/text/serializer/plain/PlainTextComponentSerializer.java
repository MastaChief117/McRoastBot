// APPROXIMATE stub for offline syntax/type checking ONLY.
// `next` is a TEST HOOK: the offline harness sets the fake chat content.
package net.kyori.adventure.text.serializer.plain;

import net.kyori.adventure.text.Component;

public class PlainTextComponentSerializer {
    public static String next = "";

    public static PlainTextComponentSerializer plainText() {
        return new PlainTextComponentSerializer();
    }

    public String serialize(Component component) {
        return next;
    }
}
