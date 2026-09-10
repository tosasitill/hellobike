package dev.local.ridecompact;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class RideApi {
    interface Events { void add(String text); }
    private final JSONObject session;
    private final Gateway.Transport transport;
    private final boolean bluetoothEnabled;
    private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
    private static final Set<String> ACTIONS=set("user.tw.ride.check","user.tw.order.check","user.ride.order.status","tw.end.module.info","tw.bike.unlock.page.basic.info","user.ride.pre.ride","user.ride.create","ride.hub.pre.close");
    RideApi(JSONObject session,Events events,boolean bluetoothEnabled) { this(session,Gateway.https(events),bluetoothEnabled); }
    RideApi(JSONObject session,Gateway.Transport transport,boolean bluetoothEnabled) { this.session=session; this.transport=transport; this.bluetoothEnabled=bluetoothEnabled; }
    JSONObject call(String action,JSONObject fields) throws Exception {
        if(!ACTIONS.contains(action)) throw new IllegalArgumentException("不支持的接口");
        boolean query=action.startsWith("user.tw.") || action.equals("user.ride.order.status");
        String endpointHost=query?"ebike.hellobike.com":"bike.hellobike.com";
        JSONObject payload=new JSONObject();
        for(String key:new String[]{"token","ticket","systemCode","version","h5Version"}) if(!session.optString(key).isEmpty()) payload.put(key,session.optString(key));
        payload.put("appName","AppHelloMiniBrand").put("releaseVersion",session.optString("h5Version","7.0.30"))
            .put("from","h5").put("sourceMarkup","ohoSaaS").put("action",action);
        for(var keys=fields.keys();keys.hasNext();) { String key=keys.next(); payload.put(key,fields.get(key)); }
        String path;
        if(action.equals("user.ride.pre.ride") || action.equals("tw.bike.unlock.page.basic.info") || action.equals("user.tw.ride.check")) path="/api/"+action;
        else if(action.equals("tw.end.module.info")) path="/api?tw.end.module.info";
        else if(action.equals("user.ride.create")) path="/api?user.ride.create";
        else if(action.equals("ride.hub.pre.close")) path="/api?ride.hub.pre.close";
        else path="/api";
        return transport.post("https://"+endpointHost+path,payload);
    }
    JSONObject locationFields(double[] location) throws Exception {
        return new JSONObject().put("lat",String.valueOf(location[0])).put("lng",String.valueOf(location[1]))
            .put("cityCode",session.getString("cityCode")).put("adCode",session.getString("adCode"));
    }
    JSONObject checkRide() throws Exception { return call("user.tw.ride.check",new JSONObject().put("bizType",0)); }
    JSONObject end(String order) throws Exception { return call("tw.end.module.info",new JSONObject().put("rideGuid",order).put("bizType",0)); }
    JSONObject prepare(String bike,double[] location) throws Exception {
        JSONObject existing=checkRide(),active=existing.optJSONObject("rideInfo");
        if(!existing.optBoolean("_noRide") && (active==null || active.optInt("rideStatus",-1)!=2)) throw new IllegalStateException("当前订单尚未结束或响应不明确，请刷新订单");
        String scanId=session.getString("userGuid")+"_"+bike+"_"+System.currentTimeMillis()+"_compact";
        JSONObject params=locationFields(location).put("bikeNo",bike).put("scanReqId",scanId).put("scan_req_id",scanId)
            .put("model",0).put("force",0).put("platform",1).put("scanEntrance",1)
            .put("openBluetooth",bluetoothEnabled?1:0).put("bleStatus",bluetoothEnabled).put("locationStatus",1).put("pageVersion",2)
            .put("mode",0).put("bizType",0);
        JSONObject pre=call("user.ride.pre.ride",params);
        int causeType=pre.optInt("causeType",-1);
        if(!pre.optBoolean("result") || causeType!=1100 || pre.optString("orderGuid").isEmpty()) throw ApiResponse.business("user.ride.pre.ride",pre,"预校验未通过");
        if(pre.optBoolean("missBike")) throw new IllegalStateException("预校验返回车型不在可用范围");
        int bikeType=pre.optInt("bikeType",0);
        if(bikeType!=0 && bikeType!=2) throw new IllegalStateException("预校验返回车型不在可用范围");
        JSONObject page=call("tw.bike.unlock.page.basic.info",locationFields(location).put("bikeNo",bike).put("bikeShowNo",bike).put("businessType",0).put("pageVersion",2));
        JSONObject price=page.optJSONObject("ridePriceInfo");
        if(price==null || price.optString("priceRule").isEmpty()) throw new IllegalStateException("未取得计费规则；未发送创建请求");
        boolean specialBike=bikeType==2;
        return new JSONObject().put("orderGuid",pre.getString("orderGuid")).put("priceRule",price.getString("priceRule")).put("specialBike",specialBike);
    }
    JSONObject create(String bike,String order,double[] location) throws Exception {
        // mode=2 is reserved by the official client for red-packet state, not bike category.
        return call("user.ride.create",locationFields(location).put("bikeNo",bike).put("orderGuid",order).put("model",0).put("mode",0)
            .put("force",1).put("rideLicenseforce",1).put("platform",1).put("connectBluetooth",false)
            .put("insurePlanType",-1).put("userConfirmContinueRide",false));
    }
    JSONObject orderStatus(String order) throws Exception { return call("user.ride.order.status",new JSONObject().put("rideGuid",order).put("bizType",1)); }
    JSONObject preClose(String order,String bike,double[] location,int poll,int operation) throws Exception {
        return call("ride.hub.pre.close",locationFields(location).put("orderGuid",order).put("bikeNo",bike)
            .put("operateType",operation).put("poll",poll).put("hasIllegalEdu",false).put("locRepTime",System.currentTimeMillis()));
    }
}
