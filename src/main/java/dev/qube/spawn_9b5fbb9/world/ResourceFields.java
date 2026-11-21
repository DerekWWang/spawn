package dev.qube.spawn_9b5fbb9.world;

import java.util.*;

/** Pure, immutable spatial fields: no chunk access, global RNG, or generation-order state. */
public final class ResourceFields {
    private ResourceFields(){}
    public enum Plant { FIBER, YARROW, BRAMBLE, RICE, MAIZE, WHEAT, ROOTS, GOURD }
    public enum Mineral { NONE, COPPER, IRON, TIN, COAL }
    public enum Host { NONE, GRANITE, LIMESTONE, SANDSTONE, STONE }
    public record Point(int x,int z){}
    public record Patch(int x,int z,int radius,int count,Plant plant,long salt){}
    public record District(Mineral mineral,Host host,int x,int y,int z,int length,int width,int height,double angle,double grade,long salt){}
    public record DepositSample(Mineral mineral,Host host,boolean ore,double grade){}
    public static long hash(long seed,long x,long z,long channel){
        long value=seed+x*0x9E3779B97F4A7C15L+z*0xC2B2AE3D27D4EB4FL+channel*0x165667B19E3779F9L;
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;value=(value^(value>>>27))*0x94D049BB133111EBL;return value^(value>>>31);
    }
    public static double unit(long value){return (value>>>11)*0x1.0p-53;}
    private static int number(long value,int bound){return (int)Math.floorMod(value,bound);}
    public static List<Patch> surfacePatches(long seed,int chunkX,int chunkZ){
        var result=new ArrayList<Patch>();int cellX=Math.floorDiv(chunkX,2),cellZ=Math.floorDiv(chunkZ,4);
        long minX=(long)chunkX*16,minZ=(long)chunkZ*16;
        for(int cx=cellX-1;cx<=cellX+1;cx++)for(int cz=cellZ-1;cz<=cellZ+1;cz++){
            long salt=hash(seed,cx,cz,11);int x=cx*32+number(salt,32),z=cz*64+number(hash(salt,0,0,2),64),radius=3+number(hash(salt,0,0,3),4);
            if(x+radius<minX||x-radius>minX+15||z+radius<minZ||z-radius>minZ+15)continue;
            result.add(new Patch(x,z,radius,5+number(hash(salt,0,0,4),8),Plant.values()[number(hash(salt,0,0,5),8)],salt));
        }
        return List.copyOf(result);
    }
    public static List<Point> patchPositions(Patch patch){
        var candidates=new ArrayList<Point>();
        for(int dx=-patch.radius;dx<=patch.radius;dx++)for(int dz=-patch.radius;dz<=patch.radius;dz++)if(dx*dx+dz*dz<=patch.radius*patch.radius)candidates.add(new Point(patch.x+dx,patch.z+dz));
        candidates.sort(Comparator.comparingLong(p->hash(patch.salt,p.x,p.z,17)));
        return List.copyOf(candidates.subList(0,Math.min(patch.count,candidates.size())));
    }
    public static District district(long seed,int cellX,int cellZ){
        long salt=hash(seed,cellX,cellZ,37);double selection=unit(salt);
        Mineral mineral=selection<.2?Mineral.NONE:selection<.48?Mineral.COPPER:selection<.72?Mineral.IRON:selection<.84?Mineral.TIN:Mineral.COAL;
        Host host=switch(mineral){case COPPER->Host.LIMESTONE;case TIN->Host.GRANITE;case COAL->Host.SANDSTONE;default->Host.STONE;};
        int x=cellX*256+48+number(hash(salt,0,0,1),160),z=cellZ*256+48+number(hash(salt,0,0,2),160);
        int y=switch(mineral){case COPPER->36;case TIN->20;case IRON->28;case COAL->44;default->16;}+number(hash(salt,0,0,3),33);
        return new District(mineral,host,x,y,z,20+number(hash(salt,0,0,4),29),16+number(hash(salt,0,0,5),17),4+number(hash(salt,0,0,6),5),unit(hash(salt,0,0,7))*Math.PI,.15+unit(hash(salt,0,0,8))*.15,salt);
    }
    public static DepositSample sampleDeposit(long seed,int x,int y,int z){return sampleDeposit(district(seed,Math.floorDiv(x,256),Math.floorDiv(z,256)),x,y,z);}
    /** Large geological provinces bias composition; barren districts remain deliberately common. */
    public static District district(long seed,int cellX,int cellZ,int referenceHeight,double relief,int minY,int maxY){
        var old=district(seed,cellX,cellZ);long province=hash(seed,Math.floorDiv(cellX,8),Math.floorDiv(cellZ,8),301);
        int setting=number(province,4);double pick=unit(hash(old.salt,0,0,302));
        Mineral[] minerals=switch(setting){case 0->new Mineral[]{Mineral.TIN,Mineral.COPPER,Mineral.IRON};case 1->new Mineral[]{Mineral.COAL,Mineral.IRON,Mineral.COPPER};case 2->new Mineral[]{Mineral.COPPER,Mineral.IRON,Mineral.TIN};default->new Mineral[]{Mineral.NONE,Mineral.COAL,Mineral.IRON};};
        Mineral mineral=pick<.30?Mineral.NONE:minerals[pick<.72?0:pick<.90?1:2];
        Host host=switch(mineral){case TIN->Host.GRANITE;case COAL->Host.SANDSTONE;case COPPER->setting==0?Host.GRANITE:Host.LIMESTONE;default->Host.STONE;};
        int depth=(mineral==Mineral.COAL?14:mineral==Mineral.TIN?35:24)+number(hash(old.salt,0,0,303),25);
        int y=Math.clamp(referenceHeight-depth,minY+12,maxY-25);
        return new District(mineral,host,old.x,y,old.z,old.length,old.width,old.height,old.angle,old.grade,old.salt);
    }
    public static Host provinceHost(long seed,int x,int z){return new Host[]{Host.GRANITE,Host.SANDSTONE,Host.LIMESTONE,Host.STONE}[number(hash(seed,Math.floorDiv(x,2048),Math.floorDiv(z,2048),301),4)];}
    public static DepositSample sampleDeposit(District district,int x,int y,int z){
        if(district.mineral==Mineral.NONE)return null;
        double dx=x-district.x,dz=z-district.z,cos=Math.cos(district.angle),sin=Math.sin(district.angle);
        double along=dx*cos+dz*sin,across=-dx*sin+dz*cos;
        double vertical=y-district.y-along*.065;
        double radius=along*along/(district.length*district.length/4d)+across*across/(district.width*district.width/4d)+vertical*vertical/(district.height*district.height/4d);
        // A narrow mineralized feeder can reach an actual surface exposure above a deeper body.
        boolean feeder=district.mineral!=Mineral.COAL&&Math.abs(across)<1.2&&Math.abs(along)<2&&y>=district.y&&y<=district.y+18;
        if(radius>1&&!feeder)return null;
        double grade=feeder?.18:district.grade;
        int layer=(int)Math.floor(vertical);boolean mineralLayer=true;double conditionalGrade=grade;
        if(district.mineral==Mineral.COAL){mineralLayer=Math.floorMod(layer,3)<2;conditionalGrade=grade*1.5;}
        else if(district.mineral==Mineral.IRON){mineralLayer=Math.floorMod(layer,4)<2;conditionalGrade=grade*2;}
        boolean ore=mineralLayer&&unit(hash(district.salt,x,z,y))<conditionalGrade;
        return new DepositSample(district.mineral,district.host,ore,grade);
    }
    public static Host quarry(long seed,int x,int z){
        int cx=Math.floorDiv(x,128),cz=Math.floorDiv(z,128);long salt=hash(seed,cx,cz,71);
        if(unit(salt)>.55)return Host.NONE;
        int centerX=cx*128+40+number(hash(salt,0,0,1),48),centerZ=cz*128+40+number(hash(salt,0,0,2),48);
        int radius=20+number(hash(salt,0,0,3),21);double dx=x-centerX,dz=z-centerZ;
        return dx*dx+dz*dz<radius*radius?new Host[]{Host.GRANITE,Host.LIMESTONE,Host.SANDSTONE}[number(hash(salt,0,0,4),3)]:Host.NONE;
    }
    /** Exact per-position probabilities; the adapter supplies real habitat classifications. */
    public static boolean looseStone(long seed,int x,int z,boolean rocky){return unit(hash(seed,x,z,91))<(rocky?.03:.001);}
    public static boolean fallenStick(long seed,int x,int z){return unit(hash(seed,x,z,92))<.018;}
    /** Fibre plants of the woodland floor, about two to a chunk of wooded ground. */
    public static boolean undergrowth(long seed,int x,int z){return unit(hash(seed,x,z,93))<.008;}
    /** Dye and medicine plants: one candidate stand to a 48-block cell, kept apart from the food and fibre patches. */
    public enum Herb { WOAD, INDIGO, MADDER, WELD, MEADOWSWEET, SPHAGNUM, CINCHONA }
    public record HerbPatch(int x,int z,int radius,int count,Herb herb,long salt){}
    public static List<HerbPatch> herbPatches(long seed,int chunkX,int chunkZ){
        var result=new ArrayList<HerbPatch>();int cellX=Math.floorDiv(chunkX,3),cellZ=Math.floorDiv(chunkZ,3);long minX=(long)chunkX*16,minZ=(long)chunkZ*16;
        for(int cx=cellX-1;cx<=cellX+1;cx++)for(int cz=cellZ-1;cz<=cellZ+1;cz++){
            long salt=hash(seed,cx,cz,211);int x=cx*48+number(salt,48),z=cz*48+number(hash(salt,0,0,2),48),radius=2+number(hash(salt,0,0,3),3);
            if(x+radius<minX||x-radius>minX+15||z+radius<minZ||z-radius>minZ+15)continue;
            result.add(new HerbPatch(x,z,radius,3+number(hash(salt,0,0,4),5),Herb.values()[number(hash(salt,0,0,5),Herb.values().length)],salt));
        }
        return List.copyOf(result);
    }
    public static List<Point> patchPositions(HerbPatch patch){return patchPositions(new Patch(patch.x,patch.z,patch.radius,patch.count,Plant.FIBER,patch.salt));}
    /** Spring greens and pulses, and the wild coffee and tea that cultivation begins from: one candidate stand to a 32-block cell. */
    public enum Forage { GARLIC, PEAS, COFFEE, TEA }
    public record ForagePatch(int x,int z,int radius,int count,Forage forage,long salt){}
    public static List<ForagePatch> foragePatches(long seed,int chunkX,int chunkZ){
        var result=new ArrayList<ForagePatch>();int cellX=Math.floorDiv(chunkX,2),cellZ=Math.floorDiv(chunkZ,2);long minX=(long)chunkX*16,minZ=(long)chunkZ*16;
        for(int cx=cellX-1;cx<=cellX+1;cx++)for(int cz=cellZ-1;cz<=cellZ+1;cz++){
            long salt=hash(seed,cx,cz,223);int x=cx*32+number(salt,32),z=cz*32+number(hash(salt,0,0,2),32),radius=2+number(hash(salt,0,0,3),3);
            if(x+radius<minX||x-radius>minX+15||z+radius<minZ||z-radius>minZ+15)continue;
            result.add(new ForagePatch(x,z,radius,4+number(hash(salt,0,0,4),5),Forage.values()[number(hash(salt,0,0,5),Forage.values().length)],salt));
        }
        return List.copyOf(result);
    }
    public static List<Point> patchPositions(ForagePatch patch){return patchPositions(new Patch(patch.x,patch.z,patch.radius,patch.count,Plant.FIBER,patch.salt));}
    /** The climate each wild forage plant lives in. {@code c} is the mean air temperature in degrees C.
     * Ramsons carpet damp temperate broadleaf woods; wild peas grow in open warm-temperate grass and scrub; wild arabica is a
     * shrub of the tropical forest understory; tea is a plant of warm, wet hill forest from the subtropics into the tropics. */
    public static boolean forageClimate(Forage forage,double c,boolean wood,boolean wet,boolean jungle,boolean taiga){
        return switch(forage){
            case GARLIC->wood&&!jungle&&!taiga&&c>=5&&c<=16;
            case PEAS->!wood&&c>=8&&c<=22;
            case COFFEE->jungle&&c>=18;
            case TEA->wood&&!taiga&&(wet||jungle)&&c>=13&&c<=28;
        };
    }
    /** What the strandline carries. Shells lie on any shore; the dye whelk and the pearl oyster need a warm sea. */
    public enum Shore { NONE, SHELLS, MUREX, PEARL }
    public static Shore shore(long seed,int x,int z,boolean beach,boolean sea,boolean warm){
        double roll=unit(hash(seed,x,z,94));
        if(roll<(beach?.02:.004))return Shore.SHELLS;
        if(!sea||!beach)return Shore.NONE;
        if(warm&&roll<.026)return Shore.MUREX;
        if(warm&&roll<.0268)return Shore.PEARL;
        return Shore.NONE;
    }
    /** Whether the field offers this column anything at all, before the costly look at its biome and water. */
    public static boolean shoreCandidate(long seed,int x,int z,boolean beach){return unit(hash(seed,x,z,94))<(beach?.0268:.004);}
    /** About a third of limestone quarry bodies were baked to marble; decided for the whole body, so a quarry is one stone. */
    public static boolean marble(long seed,int x,int z){return unit(hash(seed,Math.floorDiv(x,128),Math.floorDiv(z,128),72))<.35;}
    /** Lapis lazuli is a mineral of contact-metamorphosed limestone: a rare blue knot in the marble. */
    public static boolean lapis(long seed,int x,int y,int z){return unit(hash(seed^0x1A915L,x,z,y*31L+73))<.012;}
    public static List<Point> grove(long seed,int chunkX,int chunkZ){
        int cx=Math.floorDiv(chunkX,8),cz=Math.floorDiv(chunkZ,8);long salt=hash(seed,cx,cz,99);var result=new ArrayList<Point>();
        int centerX=cx*128+16+number(hash(salt,0,0,1),96),centerZ=cz*128+16+number(hash(salt,0,0,2),96),count=2+number(hash(salt,0,0,3),4);
        for(int i=0;i<count;i++){
            double angle=i*Math.PI*2/count;int x=centerX+(int)Math.round(Math.cos(angle)*6),z=centerZ+(int)Math.round(Math.sin(angle)*6);
            if(Math.floorDiv(x,16)==chunkX&&Math.floorDiv(z,16)==chunkZ)result.add(new Point(x,z));
        }
        return List.copyOf(result);
    }
}
