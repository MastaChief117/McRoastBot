// APPROXIMATE stub for offline syntax/type checking ONLY.
// toString() returns the content so offline tests can assert on broadcasts.
package net.kyori.adventure.text;

import net.kyori.adventure.text.format.NamedTextColor;

public interface Component {
    static Component text(String content, NamedTextColor color) {
        final String c = content;
        return new Component() {
            @Override
            public String toString() {
                return c;
            }
        };
    }
}
