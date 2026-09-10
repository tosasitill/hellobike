package dev.local.ridecompact;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class Gateway {
    interface Transport { JSONObject post(String endpoint,JSONObject payload) throws Exception; }
    private static String read(HttpsURLConnection conn,int http) throws Exception {
        try(InputStream input=http>=400?conn.getErrorStream():conn.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            if(input!=null) { byte[] buffer=new byte[8192]; int n; while((n=input.read(buffer))!=-1) {
                if(output.size()+n>2*1024*1024) throw new IllegalStateException("响应超过大小限制"); output.write(buffer,0,n);
            }}
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** LBS reverse geocoding lives on its own host and speaks text/plain; only the JSON envelope differs. */
    static Transport lbs(RideApi.Events events) {
        return (endpoint,payload)->{
            URL url=new URL(endpoint);
            String action=payload.getString("action");
            if(!"https".equals(url.getProtocol()) || !"hello-location-based-services.hellobike.com".equals(url.getHost()) || url.getUserInfo()!=null || url.getPort()!=-1) throw new IllegalArgumentException("定位反查网关不在允许列表");
            HttpsURLConnection conn=(HttpsURLConnection)url.openConnection();
            conn.setInstanceFollowRedirects(false); conn.setConnectTimeout(10000); conn.setReadTimeout(12000);
            conn.setRequestMethod("POST"); conn.setRequestProperty("Content-Type","text/plain;charset=UTF-8");
            conn.setRequestProperty("Accept","application/json"); conn.setRequestProperty("User-Agent","RideCompact/0.2 Android");
            conn.setDoOutput(true);
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8); conn.setFixedLengthStreamingMode(bytes.length);
            try {
                try(var output=conn.getOutputStream()) { output.write(bytes); }
                int http=conn.getResponseCode();
                String text=read(conn,http);
                events.add(action+" · HTTP "+http);
                if(http!=200) throw new ApiResponse.Failure(action,http,-1,"HTTP 错误；请求未自动重试");
                try { return new JSONObject(text); }
                catch(Exception e) { throw new ApiResponse.Failure(action,-1000,-1,"响应不是 JSON，可能需要官方网络协议"); }
            } finally { conn.disconnect(); }
        };
    }

    static Transport https(RideApi.Events events) {
        return (endpoint,payload)->{
            URL url=new URL(endpoint);
            String action=payload.getString("action");
            if(!"https".equals(url.getProtocol()) || !("api.hellobike.com".equals(url.getHost()) || "bike.hellobike.com".equals(url.getHost()) || "oraclelog.hellobike.cn".equals(url.getHost()) || "ebike.hellobike.com".equals(url.getHost())) || url.getUserInfo()!=null || url.getPort()!=-1) throw new IllegalArgumentException("网关不在允许列表");
            HttpsURLConnection conn=(HttpsURLConnection)url.openConnection();
            conn.setInstanceFollowRedirects(false); conn.setConnectTimeout(10000); conn.setReadTimeout(12000);
            conn.setRequestMethod("POST"); conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            conn.setRequestProperty("Accept","application/json"); conn.setRequestProperty("User-Agent","RideCompact/0.2 Android");
            conn.setRequestProperty("inner_action",action);
            conn.setDoOutput(true);
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8); conn.setFixedLengthStreamingMode(bytes.length);
            try {
                try(var output=conn.getOutputStream()) { output.write(bytes); }
                int http=conn.getResponseCode();
                String text=read(conn,http);
                try {
                    JSONObject result=ApiResponse.decode(action,http,text);
                    events.add(action+" · HTTP "+http+(result.optBoolean("_noRide")?" · 301 无骑行中订单":" · 成功"));
                    return result;
                } catch(ApiResponse.Failure e) { events.add(e.getMessage()); throw e; }
            } finally { conn.disconnect(); }
        };
    }
}
