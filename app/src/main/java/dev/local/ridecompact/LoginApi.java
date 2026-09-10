package dev.local.ridecompact;

import org.json.JSONObject;

final class LoginApi {
    static final class CaptchaRequired extends IllegalStateException {
        CaptchaRequired() { super("需要完成滑块验证"); }
    }
    private final Gateway.Transport transport;
    private final String clientId,signature;
    LoginApi(Gateway.Transport transport,String clientId,String signature) { this.transport=transport; this.clientId=clientId; this.signature=signature; }
    static void phone(String phone) { if(!phone.matches("1[3-9][0-9]{9}")) throw new IllegalArgumentException("请输入 11 位手机号"); }
    private JSONObject base(String action,String phone,String city,String adCode) throws Exception {
        phone(phone);
        if(!city.matches("[0-9]{3,6}") || !adCode.matches("[0-9]{6}")) throw new IllegalArgumentException("请填写当前城市和行政区编码");
        return new JSONObject().put("action",action).put("mobile",phone).put("systemCode","62").put("version","6.99.71")
            .put("clientId",clientId).put("cityCode",city).put("adCode",adCode);
    }
    void send(String phone,String city,String adCode) throws Exception {
        JSONObject data=transport.post("https://api.hellobike.com/api",base("user.account.sendCodeV3",phone,city,adCode).put("source",0));
        int captcha=data.optInt("captchaType",0);
        if(captcha==1) throw new CaptchaRequired();
        if(!data.optBoolean("ok")) throw new ApiResponse.Failure("user.account.sendCodeV3",0,captcha,captcha!=0?"服务器要求另一种交互验证码，当前未完成验证":"服务器未确认短信发送");
    }
    JSONObject slide(String phone,String city,String adCode) throws Exception {
        return transport.post("https://api.hellobike.com/api",base("user.account.slideCaptcha",phone,city,adCode).put("source",0));
    }
    void submitSlide(String phone,String city,String adCode,int offset) throws Exception {
        if(offset<0 || offset>4096) throw new IllegalArgumentException("滑块位置不正确");
        JSONObject data=transport.post("https://api.hellobike.com/api",base("user.account.sendCodeV3",phone,city,adCode).put("source",0).put("captcha",String.valueOf(offset)));
        if(!data.optBoolean("ok")) throw new ApiResponse.Failure("user.account.sendCodeV3",0,-1,"滑块验证未通过，短信尚未确认发送");
    }
    JSONObject login(String phone,String code,String city,String adCode,String loginId) throws Exception {
        phone(phone);
        if(!code.matches("[0-9]{4,8}")) throw new IllegalArgumentException("请输入短信验证码");
        JSONObject payload=base("user.account.login",phone,city,adCode).put("code",code).put("appChannel","ridecompact")
            .put("ssid",clientId).put("loginGuid",loginId).put("apkSignHash",signature);
        JSONObject data=transport.post("https://api.hellobike.com/auth",payload);
        if(data.optBoolean("unavailableMobile") || "1".equals(data.optString("needRebindMobile"))) throw new IllegalStateException("此账号需要额外的手机号处理，登录尚未完成");
        String guid=data.optString("guid"),token=data.optString("token");
        if(token.length()<16 || guid.length()<16) throw new IllegalStateException("登录响应缺少 token/guid，不能认定登录成功");
        return new JSONObject().put("token",token).put("ticket",data.optString("ticket")).put("userGuid",guid)
            .put("systemCode","62").put("version","6.99.71").put("h5Version","7.0.30").put("cityCode",city).put("adCode",adCode);
    }
}
