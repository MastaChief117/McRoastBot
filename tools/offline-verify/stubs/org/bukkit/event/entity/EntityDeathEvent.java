// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.event.entity;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;

public class EntityDeathEvent extends EntityEvent {
    @Override
    public LivingEntity getEntity() {
        return null;
    }

    public DamageSource getDamageSource() {
        return null;
    }
}
