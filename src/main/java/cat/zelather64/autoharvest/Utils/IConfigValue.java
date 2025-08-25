package cat.zelather64.autoharvest.Utils;

import com.google.gson.JsonObject;

public interface IConfigValue<T> {
    T getValue();
    void setValue(T value);
    String getName();
    void loadFromJson(JsonObject json);
    void saveToJson(JsonObject json);
}
