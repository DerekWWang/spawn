package dev.qube.spawn_9b5fbb9.survival;

import dev.qube.spawn_9b5fbb9.environment.EnvironmentModel;
import static dev.qube.spawn_9b5fbb9.environment.EnvironmentModel.*;

public final class EnvironmentModelTests {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    public static int run(){
        var low=new Terrain(0,10,.7,.55,0,0);var high=new Terrain(240,90,.7,.55,0,0);var settings=new Settings("tectonic",0,28,2);
        check(baseline(high)<baseline(low)-10,"High terrain must be colder than otherwise equal lowlands");
        var coldRoof=new SurvivalState();var coldOpen=new SurvivalState();var coldRoom=new SurvivalState();
        coldRoof.advance(DAY,SurvivalState.Activity.IDLE,new SurvivalState.Climate(-20,0,false,true,false,10,0),false,false);
        coldOpen.advance(DAY,SurvivalState.Activity.IDLE,new SurvivalState.Climate(-20,0,false,false,false,10,0),false,false);
        coldRoom.advance(DAY,SurvivalState.Activity.IDLE,new SurvivalState.Climate(-20,0,false,true,false,10,1),false,false);
        check(Math.abs(coldRoof.temperature-coldOpen.temperature)<1e-9,"A roof alone cannot create warm air or stop crosswind");
        check(coldRoom.temperature>coldRoof.temperature,"Walls reduce wind exposure independently of roof cover");
        check(soilWater(.5,0,34,12,false,DAY)<.3,"Exposed hot dry plots lose useful soil water");
        check(soilWater(.3,.9,16,2,false,DAY)>.55,"Rain recharges real soil moisture");
        check(soilWater(.1,0,34,12,true,DAY)>.1,"Physical adjacent water replenishes a dry plot");
        double whole=soilWater(.5,.3,18,6,false,DAY),split=.5;for(int i=0;i<1200;i++)split=soilWater(split,.3,18,6,false,20);
        check(Math.abs(whole-split)<1e-9,"Equal conditions integrate consistently across loaded and catch-up intervals");
        double useful=0,w=.4;for(int i=0;i<1200;i++){useful+=moistTicks(w,0,34,12,false,20,.25);w=soilWater(w,0,34,12,false,20);}
        check(Math.abs(useful-moistTicks(.4,0,34,12,false,DAY,.25))<1e-7,"Growth threshold crossings compose without random-tick-dependent credit");
        double rainMin=1,rainMax=0,tempMin=100,tempMax=-100;
        for(long seed:new long[]{42,918273,20261107})for(int x=-4096;x<=4096;x+=1024)for(long tick=0;tick<4*28*DAY;tick+=6000){
            var a=weather(seed,x,-1024,tick,low,settings);var b=weather(seed,x+1,-1024,tick+1,low,settings);
            check(a.equals(weather(seed,x,-1024,tick,low,settings)),"Weather is reproducible without mutable RNG state");
            check(Double.isFinite(a.temperature())&&a.rain()>=0&&a.rain()<=1&&a.wind()>=0&&a.wind()<=24,"Climate values remain bounded");
            check(Math.abs(a.rain()-b.rain())<.02&&Math.abs(a.temperature()-b.temperature())<.2,"Neighboring positions/times have smooth weather");
            rainMin=Math.min(rainMin,a.rain());rainMax=Math.max(rainMax,a.rain());tempMin=Math.min(tempMin,a.temperature());tempMax=Math.max(tempMax,a.temperature());
        }
        check(rainMax>.6&&rainMin<.05,"Regional weather includes wet spells and dry spells");
        check(tempMax-tempMin>15,"Seasons and daily weather produce meaningful temperature variation");
        for(int cx=-16;cx<16;cx++){
            var a=dev.qube.spawn_9b5fbb9.world.ResourceFields.district(42,cx,-1,80,10,-64,640);
            var b=dev.qube.spawn_9b5fbb9.world.ResourceFields.district(42,cx,-1,480,10,-64,640);
            check(b.y()-a.y()==400&&b.y()>320,"District depth follows one fixed terrain anchor even above vanilla height");
            check(a.x()==b.x()&&a.z()==b.z()&&a.mineral()==b.mineral(),"Elevation preserves the identity and horizontal footprint of finite deposits");
        }
        boolean rejected=false;try{new Settings("future",0,28,2);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Unknown profiles cannot silently change a world");
        return count;
    }
}
