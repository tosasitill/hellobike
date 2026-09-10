package dev.local.ridecompact;

import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Resolves the official city/administrative codes for a coordinate through the same LBS
 * reverse-geocode endpoint the official client uses, so the values stay identical to the
 * ones the business API expects instead of being guessed locally.
 */
final class RegionApi {
    static final String ENDPOINT = "https://hello-location-based-services.hellobike.com/api?hello.lbs.code.regeo";
    static final String ACTION = "hello.lbs.code.regeo";
    static final String APP_KEY = "app_hellobike_oho_wechat";
    /**
     * The captured request carries a mini-program signature. The server requires the field to be
     * present but does not check its value (probed 2026-09-10: a fixed placeholder returned the same
     * result as the captured one). The official signing algorithm is deliberately not replicated;
     * if the server starts validating it this call fails and the UI keeps manual entry.
     */
    static final String PLACEHOLDER_SIGNATURE = "0000000000000000000000000000dead";

    static final class Region {
        final String cityCode, adCode, province, city, district;
        Region(String cityCode, String adCode, String province, String city, String district) {
            this.cityCode = cityCode; this.adCode = adCode; this.province = province; this.city = city; this.district = district;
        }
        /** Human readable label; municipalities report an empty city and a province instead. */
        String label() {
            String top = city == null || city.isEmpty() ? province : city;
            if (top == null || top.isEmpty()) return district == null ? "" : district;
            return district == null || district.isEmpty() ? top : top + " " + district;
        }
    }

    private final Gateway.Transport transport;
    RegionApi(Gateway.Transport transport) { this.transport = transport; }

    static JSONObject payload(String mobileModel, String mobileSystem, double lat, double lng, long timestamp) throws Exception {
        JSONObject location = new JSONObject().put("lng", lng).put("lat", lat);
        JSONObject arg0 = new JSONObject().put("locations", new JSONArray().put(location)).put("extensions", "all");
        JSONObject arg1 = new JSONObject().put("appKey", APP_KEY).put("signature", PLACEHOLDER_SIGNATURE)
            .put("src", "SMALL_PROGRAM").put("timestamp", timestamp);
        return new JSONObject()
            .put("riskControlData", new JSONObject())
            .put("version", "7.0.21").put("releaseVersion", "7.0.21").put("systemCode", "62")
            .put("appName", "AppOhoSaaSCore").put("mobileModel", mobileModel).put("weChatVersion", "UNKNOW")
            .put("mobileSystem", mobileSystem).put("SDKVersion", "UNKNOW").put("systemPlatform", "Android")
            .put("from", "h5").put("CODE_ENV", "pro").put("action", ACTION)
            .put("arg0", arg0.toString()).put("arg1", arg1).put("method", "reGeoCodingSearch").put("__sysTag", "base");
    }

    static Region parse(JSONObject envelope) throws Exception {
        int code = envelope.optInt("code", Integer.MIN_VALUE);
        JSONObject data = envelope.optJSONObject("data");
        if (code != 0 || data == null) {
            int infoCode = data == null ? -1 : data.optInt("infoCode", -1);
            throw new ApiResponse.Failure(ACTION, code, -1, "定位反查被拒绝（infoCode=" + infoCode + "）");
        }
        JSONObject inner = data.optJSONObject("data");
        JSONArray list = inner == null ? null : inner.optJSONArray("reGeoCodesList");
        JSONObject entry = list == null || list.length() == 0 ? null : list.optJSONObject(0);
        JSONObject component = entry == null ? null : entry.optJSONObject("addressComponent");
        if (component == null) throw new ApiResponse.Failure(ACTION, 0, -1, "定位反查没有返回行政区信息");
        String cityCode = component.optString("cityCode").trim(), adCode = component.optString("adCode").trim();
        if (!cityCode.matches("[0-9]{3,6}") || !adCode.matches("[0-9]{6}"))
            throw new ApiResponse.Failure(ACTION, 0, -1, "定位反查返回的城市/行政区编码不可用");
        return new Region(cityCode, adCode, component.optString("province").trim(), component.optString("city").trim(), component.optString("district").trim());
    }

    Region resolve(double lat, double lng) throws Exception {
        return parse(transport.post(ENDPOINT, payload(Build.MODEL, "Android " + Build.VERSION.RELEASE, lat, lng, System.currentTimeMillis())));
    }
}
