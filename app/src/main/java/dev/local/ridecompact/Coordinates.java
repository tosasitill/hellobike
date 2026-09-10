package dev.local.ridecompact;

final class Coordinates {
    private static final double A = 6378245.0, EE = 0.00669342162296594323;
    static double[] toGcj02(double lat, double lng) {
        if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return new double[]{lat,lng};
        double x=lng-105, y=lat-35;
        double dLat=-100+2*x+3*y+0.2*y*y+0.1*x*y+0.2*Math.sqrt(Math.abs(x));
        dLat+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dLat+=(20*Math.sin(y*Math.PI)+40*Math.sin(y/3*Math.PI))*2/3;
        dLat+=(160*Math.sin(y/12*Math.PI)+320*Math.sin(y*Math.PI/30))*2/3;
        double dLng=300+x+2*y+0.1*x*x+0.1*x*y+0.1*Math.sqrt(Math.abs(x));
        dLng+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;
        dLng+=(20*Math.sin(x*Math.PI)+40*Math.sin(x/3*Math.PI))*2/3;
        dLng+=(150*Math.sin(x/12*Math.PI)+300*Math.sin(x/30*Math.PI))*2/3;
        double rad=lat/180*Math.PI, magic=1-EE*Math.sin(rad)*Math.sin(rad), sqrt=Math.sqrt(magic);
        return new double[]{lat+dLat*180/((A*(1-EE))/(magic*sqrt)*Math.PI), lng+dLng*180/(A/sqrt*Math.cos(rad)*Math.PI)};
    }
}
