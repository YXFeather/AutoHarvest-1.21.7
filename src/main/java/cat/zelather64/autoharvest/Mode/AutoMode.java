package cat.zelather64.autoharvest.Mode;

public interface AutoMode {
    void tick();
    String getName();
    default void onDisable() {}
}
