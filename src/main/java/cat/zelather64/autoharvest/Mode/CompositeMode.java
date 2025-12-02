package cat.zelather64.autoharvest.Mode;

import java.util.List;
import java.util.stream.Collectors;

public class CompositeMode implements AutoMode{

    private enum State {
        IDLE,
        HARVESTING,
        PLANTING
    }

    private State currentState = State.HARVESTING;
    private final HarvestMode harvestMode = new HarvestMode();
    private final PlantMode plantMode = new PlantMode();
    private int stateTicks = 0;
    private static final int STATE_DURATION_TICKS = 10; // 每个状态持续10tick

    @Override
    public void tick() {
        stateTicks++;

        switch (currentState) {
            case HARVESTING:
                harvestMode.tick();

                // 切换到种植状态
                if (stateTicks >= STATE_DURATION_TICKS || isHarvestComplete()) {
                    currentState = State.PLANTING;
                    stateTicks = 0;
                }
                break;

            case PLANTING:
                plantMode.tick();

                // 切换回收获状态
                if (stateTicks >= STATE_DURATION_TICKS || isPlantComplete()) {
                    currentState = State.HARVESTING;
                    stateTicks = 0;
                }
                break;

            case IDLE:
                // 检查是否有可执行的操作
                if (hasCropsToHarvest()) {
                    currentState = State.HARVESTING;
                } else if (hasEmptyFarmland()) {
                    currentState = State.PLANTING;
                }
                break;
        }
    }

    private boolean isHarvestComplete() {
        // 实现检查收获是否完成的逻辑
        return false;
    }

    private boolean isPlantComplete() {
        // 实现检查种植是否完成的逻辑
        return false;
    }

    private boolean hasCropsToHarvest() {
        // 检查是否有成熟作物
        return true;
    }

    private boolean hasEmptyFarmland() {
        // 检查是否有可种植的耕地
        return true;
    }

    @Override
    public String getName() {
        return "智能农夫模式";
    }

    @Override
    public void onDisable() {
        harvestMode.onDisable();
        plantMode.onDisable();
    }

    //    private final List<AutoMode> modes;
//
//    public CompositeMode(AutoMode... modes){
//        this.modes = List.of(modes);
//    }
//
//    public static CompositeMode farmer(){
//        return new CompositeMode(new PlantMode(), new HarvestMode());
//    }
//
//    @Override
//    public void tick(){
//        modes.forEach(AutoMode::tick);
//    }
//
//    @Override
//    public String getName(){
//        return modes.stream().map(AutoMode::getName).collect(Collectors.joining(" + "));
//    }
//
//    public static String farmerMode_string(){
//        return farmer().getName();
//    }
//
//    @Override
//    public void onDisable(){
//        modes.forEach(AutoMode::onDisable);
//    }
}
