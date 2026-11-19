package dev.qube.spawn_9b5fbb9.survival;

import dev.qube.spawn_9b5fbb9.world.ResourceFields;
import java.util.*;

/** Deterministic spatial behavior, independent of Minecraft chunk scheduling. */
public final class ResourceFieldTests {
    public static int run(){
        int assertions=0,patches=0,barren=0;var minerals=new EnumMap<ResourceFields.Mineral,Integer>(ResourceFields.Mineral.class);
        for(int x=-20;x<20;x++)for(int z=-20;z<20;z++){
            var district=ResourceFields.district(918273L,x,z);assertions++;
            if(!district.equals(ResourceFields.district(918273L,x,z)))throw new AssertionError("District generation must be coordinate deterministic");
            if(district.mineral()==ResourceFields.Mineral.NONE)barren++;else minerals.merge(district.mineral(),1,Integer::sum);
        }
        if(barren<240||barren>400||minerals.size()!=4)throw new AssertionError("District survey must include barren land and all four mineral families");assertions++;
        var forward=new HashMap<String,ResourceFields.Patch>();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(var p:ResourceFields.surfacePatches(42L,x,z)){
            forward.put(p.x()+","+p.z(),p);patches++;
            if(p.radius()<3||p.radius()>6||p.count()<5||p.count()>12)throw new AssertionError("Patch bounds");assertions++;
            var occupied=ResourceFields.patchPositions(p);
            if(occupied.size()!=p.count()||new HashSet<>(occupied).size()!=occupied.size())throw new AssertionError("Patch positions must be finite and unique");assertions++;
        }
        if(patches<30||patches>180)throw new AssertionError("Surface patches must remain sparse over an extended region");assertions++;
        for(int x=8;x>=-8;x--)for(int z=8;z>=-8;z--)for(var p:ResourceFields.surfacePatches(42L,x,z)){
            if(!p.equals(forward.get(p.x()+","+p.z())))throw new AssertionError("Chunk evaluation order must not alter patches");assertions++;
        }
        boolean crossing=false;
        for(var p:forward.values())for(var point:ResourceFields.patchPositions(p))if(Math.floorDiv(point.x(),16)!=Math.floorDiv(p.x(),16)||Math.floorDiv(point.z(),16)!=Math.floorDiv(p.z(),16))crossing=true;
        if(!crossing)throw new AssertionError("Patches must continue across chunk borders");assertions++;
        for(var p:forward.values())for(var point:ResourceFields.patchPositions(p)){
            boolean visible=ResourceFields.surfacePatches(42L,Math.floorDiv(point.x(),16),Math.floorDiv(point.z(),16)).contains(p);
            if(!visible)throw new AssertionError("Owning chunks must see every cross-border patch position");assertions++;
        }
        for(var mineral:List.of(ResourceFields.Mineral.COAL,ResourceFields.Mineral.IRON)){
            var seam=new ResourceFields.District(mineral,ResourceFields.Host.SANDSTONE,0,40,0,40,30,8,0,.25,123L);
            for(int z=-10;z<=10;z++){
                var barrenLayer=ResourceFields.sampleDeposit(seam,0,42,z);
                if(barrenLayer!=null&&barrenLayer.ore())throw new AssertionError("Coal and iron need intervening barren strata, not isotropic ore scatter");assertions++;
            }
        }
        return assertions;
    }
}
