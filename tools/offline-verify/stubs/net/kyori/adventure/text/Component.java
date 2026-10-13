// APPROXIMATE stub for offline syntax/type checking ONLY.
package net.kyori.adventure.text;

import net.kyori.adventure.text.format.NamedTextColor;

public interface Component {
    static Component text(String content, NamedTextColor color) {
        return new Component() {
        };
    }
}
