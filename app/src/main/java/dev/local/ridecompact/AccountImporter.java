package dev.local.ridecompact;

import org.json.JSONArray;
import org.json.JSONObject;

final class AccountImporter {
    static JSONObject parse(String text) throws Exception {
        JSONObject input=new JSONObject(text);
        if(!input.has("log")) return input;
        JSONArray entries=input.getJSONObject("log").getJSONArray("entries");
        JSONObject chosen=null;
        String latest="";
        for(int i=0;i<entries.length();i++) {
            JSONObject entry=entries.getJSONObject(i);
            JSONObject request=entry.optJSONObject("request");
            JSONObject post=request==null?null:request.optJSONObject("postData");
            if(post==null) continue;
            JSONObject candidate;
            try { candidate=new JSONObject(post.optString("text")); } catch(Exception ignored) { continue; }
            String action=candidate.optString("action");
            if(!action.startsWith("user.") || candidate.optString("token").length()<16) continue;
            String time=entry.optString("startedDateTime");
            if(chosen==null || time.compareTo(latest)>0) { chosen=candidate; latest=time; }
        }
        if(chosen==null) throw new IllegalArgumentException("HAR 中未找到账号会话");
        JSONObject result=new JSONObject(); String token=chosen.getString("token");
        String[] keys={"token","ticket","userGuid","systemCode","version","h5Version","cityCode","adCode"};
        for(int i=entries.length()-1;i>=0;i--) {
            JSONObject entry=entries.getJSONObject(i),request=entry.optJSONObject("request");
            JSONObject post=request==null?null:request.optJSONObject("postData");
            if(post==null) continue;
            JSONObject candidate;
            try { candidate=new JSONObject(post.optString("text")); } catch(Exception ignored) { continue; }
            if(!token.equals(candidate.optString("token")) || !candidate.optString("action").startsWith("user.")) continue;
            for(String key:keys) if(!candidate.optString(key).isEmpty()) result.put(key,candidate.optString(key));
            JSONObject response=entry.optJSONObject("response"),content=response==null?null:response.optJSONObject("content");
            if(content!=null && "user.ride.pre.ride".equals(candidate.optString("action"))) {
                JSONObject data=new JSONObject(content.optString("text")).optJSONObject("data");
                if(data!=null && !data.optString("userGuid").isEmpty()) result.put("userGuid",data.getString("userGuid"));
            }
        }
        return result;
    }
}
