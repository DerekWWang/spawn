package dev.qube.spawn_9b5fbb9.survival;
/** Read-only telemetry, identical on a server body and its synchronized client representation. */
public interface SurvivalView {
    double getSpawnSurvivalHunger();
    double getSpawnSurvivalThirst();
    double getSpawnSurvivalTemperature();
    double getSpawnSurvivalMental();
    double getSpawnSurvivalMovement();
    double getSpawnSurvivalPottery();
    double getSpawnSurvivalSmithing();
    double getSpawnSurvivalWeaving();
    double getSpawnSurvivalFarming();
    double getSpawnSurvivalForaging();
    double getSpawnSurvivalHunting();
}
