package dev.qube.spawn_9b5fbb9.environment;

/** Deterministic environment math, independent of Minecraft and chunk state. */
public final class EnvironmentModel {
    public static final long DAY=24000;
    private EnvironmentModel(){}
    public record Terrain(double elevation,double relief,double temperature,double humidity,double continentalness,double rainShadow){}
    public record Weather(double temperature,double rain,double wind,double windX,double windZ,boolean snow){}
    public record Settings(String profile,long epoch,int seasonDays,int resources){
        public Settings {if(!java.util.Set.of("legacy","vanilla","tectonic").contains(profile)||seasonDays<4||seasonDays>112||resources<1||resources>2)throw new IllegalArgumentException("Invalid world environment settings");}
        public boolean active(){return !profile.equals("legacy");}
    }
    public static Weather weather(long seed,int x,int z,long tick,Terrain terrain,Settings settings){
        double time=(double)tick-settings.epoch;
        double front=noise(seed,(x-time*.035)/2048d,(z-time*.018)/2048d,time/(2*DAY),71);
        double anomaly=noise(seed,x/3072d,z/3072d,time/(3*DAY),83);
        double rain=clamp((front+terrain.humidity*.42-terrain.rainShadow*.42-.5)*3,0,1);
        double year=time/(4d*settings.seasonDays*DAY);
        double season=Math.sin(2*Math.PI*(year-.125))*(7+clamp(terrain.continentalness,-1,1)*2);
        double daily=Math.sin(2*Math.PI*(time/DAY-.125))*(terrain.continentalness<-.1?2:4);
        double temperature=clamp(baseline(terrain)+season+daily+(anomaly-.5)*7-rain*2,-65,55);
        double wind=clamp(2+anomaly*11+terrain.relief*.025,0,24),angle=windAngle(seed)+Math.sin(time/(3*DAY))*.25;
        return new Weather(temperature,rain,wind,Math.cos(angle),Math.sin(angle),temperature<=0);
    }
    public static double baseline(Terrain terrain){return clamp(-8+terrain.temperature*38-Math.max(0,terrain.elevation)*.065+terrain.rainShadow*2,-55,48);}
    public static double windAngle(long seed){return unit(mix(seed,0,0,23))*Math.PI*2;}
    /** Exact constant-condition solution; subdivisions cannot create or erase water. */
    public static double soilWater(double water,double rain,double temperature,double wind,boolean naturalWater,long ticks){
        if(!Double.isFinite(water)||!Double.isFinite(rain)||!Double.isFinite(temperature)||!Double.isFinite(wind)||ticks<0)throw new IllegalArgumentException("Invalid soil conditions");
        double drying=.22+Math.max(0,temperature-18)*.016+Math.max(0,wind)*.016;
        double recharge=clamp(rain,0,1)*.9+(naturalWater?.85:0),equilibrium=recharge/drying;
        return clamp(equilibrium+(clamp(water,0,1)-equilibrium)*Math.exp(-drying*ticks/DAY),0,1);
    }
    /** Exact time above a growth moisture threshold under the same constant bucket conditions. */
    public static double moistTicks(double water,double rain,double temperature,double wind,boolean naturalWater,long ticks,double threshold){
        double end=soilWater(water,rain,temperature,wind,naturalWater,ticks);water=clamp(water,0,1);
        if(water>=threshold&&end>=threshold)return ticks;if(water<threshold&&end<threshold)return 0;
        double drying=.22+Math.max(0,temperature-18)*.016+Math.max(0,wind)*.016,equilibrium=(clamp(rain,0,1)*.9+(naturalWater?.85:0))/drying;
        double crossing=clamp(-Math.log((threshold-equilibrium)/(water-equilibrium))*DAY/drying,0,ticks);
        return water>=threshold?crossing:ticks-crossing;
    }
    public static double noise(long seed,double x,double z,double time,int channel){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);long it=(long)Math.floor(time);
        double fx=smooth(x-ix),fz=smooth(z-iz),ft=smooth(time-it);double[] planes=new double[2];
        for(int dt=0;dt<2;dt++){
            long frame=mix(seed,it+dt,channel,109);
            double a=lerp(fx,unit(mix(frame,ix,iz,channel)),unit(mix(frame,ix+1,iz,channel)));
            double b=lerp(fx,unit(mix(frame,ix,iz+1,channel)),unit(mix(frame,ix+1,iz+1,channel)));
            planes[dt]=lerp(fz,a,b);
        }
        return lerp(ft,planes[0],planes[1]);
    }
    public static long mix(long seed,long x,long z,long channel){long v=seed+x*0x9E3779B97F4A7C15L+z*0xC2B2AE3D27D4EB4FL+channel*0x165667B19E3779F9L;v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;return v^(v>>>31);}
    public static double unit(long value){return (value>>>11)*0x1.0p-53;}
    private static double smooth(double t){return t*t*(3-2*t);}
    private static double lerp(double t,double a,double b){return a+(b-a)*t;}
    private static double clamp(double n,double low,double high){return Math.clamp(n,low,high);}
}
