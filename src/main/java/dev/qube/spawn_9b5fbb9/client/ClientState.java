package dev.qube.spawn_9b5fbb9.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.qube.spawn_9b5fbb9.network.Payloads;

/** What the client has been told by the server. Reset on disconnect. */
public final class ClientState {
    public static String society = "";
    public static Set<String> known = new HashSet<>();
    public static Map<String, Payloads.Status> statuses = new HashMap<>();
    public static Payloads.Quiz quiz;
    public static Payloads.CraftProgress craft = new Payloads.CraftProgress("", 0, 0, 0, false);
    public static int stationContainer = -1;
    public static List<String> stationStatus = List.of();
    public static List<String> ledger = List.of();
    /** bumped whenever anything changes, so screens can refresh cheaply */
    public static int version;

    private ClientState() {
    }

    public static void reset() {
        society = "";
        known = new HashSet<>();
        statuses = new HashMap<>();
        quiz = null;
        craft = new Payloads.CraftProgress("", 0, 0, 0, false);
        stationContainer = -1;
        stationStatus = List.of();
        ledger = List.of();
        version++;
    }

    public static boolean knows(String functionId) {
        return known.contains(functionId);
    }
}
