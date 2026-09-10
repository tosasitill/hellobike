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
            // The official parser recognizes n/m/e/x/u/s/c/p. Only n is accepted here.
            for(String other:new String[]{"m","e","x","u","s","c","p"}) if(params.containsKey(other)) throw new Exception();
            String code=params.get("n");
            if(code==null || !code.matches("[0-9]{4,16}")) throw new Exception();
            return new Result(code,"c3x.me / n");
        } catch(Exception e) { throw new IllegalArgumentException("仅支持 c3x.me 的 n 参数单车码或数字车号；其他码请使用官方 App"); }
    }
}
