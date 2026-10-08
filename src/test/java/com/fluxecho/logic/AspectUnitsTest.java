package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AspectUnitsTest {

    private static Map<String, String[]> tc() {
        Map<String, String[]> m = new HashMap<>();
        for (String p : new String[] { "aer", "terra", "ignis", "aqua", "ordo", "perditio" }) m.put(p, new String[0]);
        m.put("lux", new String[] { "aer", "ignis" });
        m.put("vacuos", new String[] { "aer", "perditio" });
        m.put("potentia", new String[] { "ordo", "ignis" });
        m.put("motus", new String[] { "aer", "ordo" });
        m.put("vitreus", new String[] { "terra", "ordo" });
        m.put("permutatio", new String[] { "motus", "aqua" });
        m.put("machina", new String[] { "motus", "instrumentum" });
        m.put("instrumentum", new String[] { "humanus", "ordo" });
        m.put("humanus", new String[] { "bestia", "cognitio" });
        m.put("bestia", new String[] { "motus", "victus" });
        m.put("victus", new String[] { "aqua", "terra" });
        m.put("cognitio", new String[] { "terra", "spiritus" });
        m.put("spiritus", new String[] { "victus", "mortuus" });
        m.put("mortuus", new String[] { "victus", "perditio" });
        return m;
    }

    @Test
    void primalsAreOneAndCompoundsAddUp() {
        AspectUnits u = new AspectUnits(tc());
        assertEquals(1, u.of("aer"));
        assertEquals(2, u.of("lux"));
        assertEquals(3, u.of("permutatio"));
        // machina = motus(2) + instrumentum(humanus(bestia(4) + cognitio(1 + spiritus(2 + 3))) + 1) = 2 + 11
        assertEquals(13, u.of("machina"));
    }

    @Test
    void unknownAspectsAndComponentsCountAsOne() {
        Map<String, String[]> m = tc();
        m.put("odd", new String[] { "aer", "missing" });
        AspectUnits u = new AspectUnits(m);
        assertEquals(1, u.of("nothing"));
        assertEquals(2, u.of("odd"));
    }

    @Test
    void cyclesAreCapped() {
        Map<String, String[]> m = new HashMap<>();
        m.put("a", new String[] { "b", "aer" });
        m.put("b", new String[] { "a", "aer" });
        assertEquals(AspectUnits.CAP, new AspectUnits(m).of("a"));
    }

    @Test
    void deepChainsAreCapped() {
        Map<String, String[]> m = new HashMap<>();
        for (int i = 0; i < 40; i++) m.put("x" + i, new String[] { "x" + (i + 1), "aer" });
        assertEquals(AspectUnits.CAP, new AspectUnits(m).of("x0"));
    }
}
