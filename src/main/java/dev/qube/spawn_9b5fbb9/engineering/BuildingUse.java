package dev.qube.spawn_9b5fbb9.engineering;

import java.util.Locale;

/** What a building is for. The use decides what the structure must provide, not what it is made of. */
public enum BuildingUse {
    GENERIC("structure", "Any structure: only its soundness is judged."),
    RESIDENCE("residence", "Where people rest: an enclosed, roofed room with a door and a bed for each resident."),
    STORAGE("storage", "Where goods are kept and counted: containers under cover. Enclosed stores keep food longer."),
    PEN("pen", "Where livestock is kept: ground closed in by fences or walls."),
    BARRIER("barrier", "A wall that holds back water: seawall, flood barrier or dam. Judged on the water pressure it resists.");

    public final String id;
    public final String description;

    BuildingUse(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public static BuildingUse byId(String id) {
        if (id != null) {
            String key = id.toLowerCase(Locale.ROOT);
            for (BuildingUse u : values()) {
                if (u.id.equals(key) || u.name().toLowerCase(Locale.ROOT).equals(key)) {
                    return u;
                }
            }
        }
        return null;
    }
}
