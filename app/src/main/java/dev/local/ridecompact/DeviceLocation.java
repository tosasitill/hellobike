package dev.local.ridecompact;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

/**
 * Returns one validated device fix, converted to GCJ02 because the reverse-geocode endpoint expects
 * the same coordinate system the official client (AMap based) uses. A WGS84 fix would land roughly
 * 500 m off and could resolve the wrong district.
 */
final class DeviceLocation {
    static final double MAX_ACCURACY_METRES = 80;
    static final long MAX_AGE_NANOS = 30_000_000_000L;

    private DeviceLocation() { }

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** @return {lat,lng} in GCJ02; throws with a user facing reason when no trusted fix exists. */
    static double[] requireGcj02(Context context) {
        if (!permitted(context)) throw new IllegalStateException("需要精确定位权限才能自动填写城市和行政区");
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) throw new IllegalStateException("此设备没有定位服务");
        Location best = null;
        for (String provider : manager.getProviders(true)) {
            Location candidate;
            try { candidate = manager.getLastKnownLocation(provider); } catch (Exception ignored) { continue; }
            if (candidate == null || !candidate.hasAccuracy() || candidate.isFromMockProvider()) continue;
            if (candidate.getAccuracy() > MAX_ACCURACY_METRES) continue;
            long age = SystemClock.elapsedRealtimeNanos() - candidate.getElapsedRealtimeNanos();
            if (age < 0 || age > MAX_AGE_NANOS) continue;
            if (best == null || candidate.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos()) best = candidate;
        }
        if (best == null) throw new IllegalStateException("还没有 30 秒内、精度优于 80 米的定位，请到开阔处重试");
        return Coordinates.toGcj02(best.getLatitude(), best.getLongitude());
    }
}
