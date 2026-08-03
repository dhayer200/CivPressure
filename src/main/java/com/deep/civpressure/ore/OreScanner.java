package com.deep.civpressure.ore;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

public final class OreScanner {
    private final Map<Material, OreType> oreTypesByMaterial = buildMaterialMap();

    public OreScanResult scan(ChunkSnapshot snapshot, int minHeight, int maxHeight) {
        OreScanResult result = new OreScanResult();

        for (int y = minHeight; y < maxHeight; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    count(snapshot.getBlockType(x, y, z), result);
                }
            }
        }
        return result;
    }

    private void count(Material material, OreScanResult result) {
        OreType oreType = oreTypesByMaterial.get(material);
        if (oreType != null) {
            result.increment(oreType);
        }
    }

    private Map<Material, OreType> buildMaterialMap() {
        Map<Material, OreType> materialMap = new EnumMap<>(Material.class);
        for (Material material : Material.values()) {
            for (OreType oreType : OreType.values()) {
                if (oreType.matches(material)) {
                    materialMap.put(material, oreType);
                    break;
                }
            }
        }
        return materialMap;
    }
}
