package dev.local.ridecompact;

import org.json.JSONObject;

final class ApiResponse {
    static final class Failure extends IllegalStateException {
        final String action;
        final int code;
        final int causeType;
        Failure(String action,int code,int causeType,String message) {
            super(action+" · code="+code+(causeType==-1?"":" · causeType="+causeType)+" · "+clean(message));
            this.action=action; this.code=code; this.causeType=causeType;
        }
    }
    static String closeReason(JSONObject data) {
        int cause=data.optInt("causeType",-1), status=data.optInt("status",-1);
        String server=data.optString("causeDesc",data.optString("cause",data.optString("message","")));
        String detail;
        switch(cause) {
            case 1101: detail="还车位置正在校验，请保持定位开启并稍后重试"; break;
            case 1102: detail="当前位于运营区外，不能在此还车。请将车辆骑回运营区或官方 App 地图显示的可还车区域，再重新校验"; break;
            case 1103: detail="还车位置校验通过，可以提交关锁"; break;
            case 1104: detail="当前位置无法完成还车校验，请检查定位权限、GPS 信号和网络"; break;
            default: detail="还车位置未通过校验，请移动到官方 App 标记的可还车区域后重试";
        }
        return detail+(server.isEmpty()?"":"；服务端说明："+server)+"（status="+status+"，causeType="+cause+"）";
    }
    static Failure closeFailure(JSONObject data) { return new Failure("ride.hub.pre.close",0,data.optInt("causeType",-1),closeReason(data)); }
    static String clean(String text) {
        if(text==null) return "无错误说明";
        String value=text.replaceAll("https?://[^\\s]+","[链接]")
            .replaceAll("(?i)[a-z0-9+/=_-]{24,}","[已隐藏]")
            .replaceAll("[0-9]{7,}","[编号已隐藏]").replaceAll("[\\r\\n\\t]"," ");
        return value.length()>240?value.substring(0,240):value;
    }
    static JSONObject decode(String action,int http,String body) throws Exception {
        if(http!=200) throw new Failure(action,http,-1,"HTTP 错误；请求未自动重试");
        JSONObject envelope;
        try { envelope=new JSONObject(body); }catch(Exception e) { throw new Failure(action,-1000,-1,"响应不是 JSON，可能需要官方网络协议"); }
        int code=envelope.optInt("code",Integer.MIN_VALUE);
        // 301 is only an empty-order result on this endpoint, not an authentication failure.
        if("user.tw.ride.check".equals(action) && code==301) return new JSONObject().put("_noRide",true);
        JSONObject data=envelope.optJSONObject("data");
        if(code!=0) throw new Failure(action,code,data==null?-1:data.optInt("causeType",-1),envelope.optString("msg","服务端拒绝"));
        if(data==null) throw new Failure(action,0,-1,"响应缺少 data 对象");
        return data;
    }
    static Failure business(String action,JSONObject data,String fallback) {
        return new Failure(action,0,data.optInt("causeType",-1),data.optString("causeDesc",data.optString("cause",data.optString("message",fallback))));
    }
}
