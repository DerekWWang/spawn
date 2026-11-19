import dev.qube.spawn_9b5fbb9.world.ResourceFields;
import java.util.*;

/** Read-only field survey and representative coordinates for saved-terrain validation. */
public final class ResourceFieldsSurvey {
    public static void main(String[] args){
        System.out.println("{\"seeds\":[");boolean firstSeed=true;
        for(long seed:new long[]{42,918273,20261107}){
            if(!firstSeed)System.out.println(",");firstSeed=false;
            var counts=new EnumMap<ResourceFields.Mineral,Integer>(ResourceFields.Mineral.class);
            var examples=new EnumMap<ResourceFields.Mineral,ResourceFields.District>(ResourceFields.Mineral.class);
            for(int x=-32;x<32;x++)for(int z=-32;z<32;z++){
                var d=ResourceFields.district(seed,x,z);counts.merge(d.mineral(),1,Integer::sum);
            }
            // Keep actual generation samples close to the origin.
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                var d=ResourceFields.district(seed,x,z);examples.putIfAbsent(d.mineral(),d);
            }
            System.out.print("{\"seed\":"+seed+",\"districts\":4096,\"distribution\":{");boolean first=true;
            for(var e:counts.entrySet()){if(!first)System.out.print(",");first=false;System.out.print("\""+e.getKey()+"\":"+e.getValue());}
            System.out.print("},\"samples\":[");first=true;
            for(var e:examples.entrySet()){
                var d=e.getValue();if(!first)System.out.print(",");first=false;int host=0,ore=0;
                for(int x=d.x()-30;x<=d.x()+30;x++)for(int z=d.z()-30;z<=d.z()+30;z++)for(int y=d.y()-12;y<=d.y()+18;y++){
                    var point=ResourceFields.sampleDeposit(d,x,y,z);if(point!=null){host++;if(point.ore())ore++;}
                }
                System.out.print("{\"mineral\":\""+e.getKey()+"\",\"x\":"+d.x()+",\"y\":"+d.y()+",\"z\":"+d.z()+",\"hostVolume\":"+host+",\"oreCandidates\":"+ore+",\"grade\":"+d.grade()+"}");
            }
            System.out.print("]}");
        }
        System.out.println("]}");
    }
}
