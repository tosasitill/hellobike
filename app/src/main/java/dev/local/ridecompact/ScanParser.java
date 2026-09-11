package dev.local.ridecompact;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class ScanParser {
    static final class Result {
        final String bikeNo;
        final String source;
        Result(String bikeNo,String source) { this.bikeNo=bikeNo; this.source=source; }
    }
    static Result parse(String raw) {
        if(raw==null || raw.length()>2048) throw new IllegalArgumentException("二维码内容无效");
        String value=raw.trim();
        if(value.matches("[0-9]{4,16}")) return new Result(value,"手输车号");
        try {
            URI uri=new URI(value);
            if(!"c3x.me".equalsIgnoreCase(uri.getHost()) || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getUserInfo()!=null || uri.getPort()!=-1) throw new Exception();
            Map<String,String> params=new HashMap<>();
            for(String pair:uri.getRawQuery().split("&")) {
                String[] parts=pair.split("=",2);
                String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8.name());
                String val=parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8.name()):"";
                if(params.put(key,val)!=null) throw new Exception();
            }
            // The official parser recognizes n/m/e/x/u/s/c/p. BikeScanExecute registers n and u for
            // ordinary bikes (ScanBean.isBikeType); the rest stay unsupported here.
            for(String other:new String[]{"m","e","x","s","c","p"}) if(params.containsKey(other)) throw new Exception();
            // CodeAnalysisKt reads the first non-empty entry of {n,m,e,x,u,s,c,p}, so n wins over u
            // and empty values are skipped. Two different codes in one QR are ambiguous and rejected.
            String n=params.get("n"),u=params.get("u");
            boolean hasN=n!=null&&!n.isEmpty(),hasU=u!=null&&!u.isEmpty();
            if(hasN&&hasU&&!n.equals(u)) throw new Exception();
            String key=hasN?"n":hasU?"u":null;
            if(key==null) throw new Exception();
            String code=params.get(key);
            if(code==null || !code.matches("[0-9]{4,16}")) throw new Exception();
            return new Result(code,"c3x.me / "+key);
        } catch(Exception e) { throw new IllegalArgumentException("仅支持 c3x.me 的 n/u 参数单车码或数字车号；其他码请使用官方 App"); }
    }
}
