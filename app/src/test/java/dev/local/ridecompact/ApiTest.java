package dev.local.ridecompact;

import org.junit.Test;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ApiTest {
    private JSONObject session() throws Exception {
        return new JSONObject().put("token","test-session-token-not-real").put("ticket","test-ticket")
            .put("userGuid","test-user-id-not-real").put("systemCode","62").put("version","6.99.71").put("h5Version","7.0.30").put("cityCode","010").put("adCode","110108");
    }
    @Test public void actual301ResponseIsNoRide() throws Exception {
        JSONObject result=ApiResponse.decode("user.tw.ride.check",200,"{\"code\":301,\"msg\":\"无骑行中订单\"}");
        assertTrue(result.getBoolean("_noRide"));
    }
    @Test public void code301OnOtherEndpointIsNotSuccess() throws Exception {
        try { ApiResponse.decode("user.ride.create",200,"{\"code\":301,\"msg\":\"other\"}"); fail(); }
        catch(ApiResponse.Failure e) { assertEquals(301,e.code); }
    }
    @Test public void malformedEmptyDataIsNotNoRide() throws Exception {
        for(String response:new String[]{"{\"code\":0,\"data\":null}","{\"code\":0}","[]","opaque"}) {
            try { ApiResponse.decode("user.tw.ride.check",200,response); fail(); } catch(ApiResponse.Failure expected){}
        }
    }
    @Test public void authenticationFailureIsPreserved() throws Exception {
        try { ApiResponse.decode("user.tw.ride.check",200,"{\"code\":-10003,\"msg\":\"登录失效\"}"); fail(); }
        catch(ApiResponse.Failure e) { assertEquals(-10003,e.code); assertTrue(e.getMessage().contains("登录失效")); }
    }
    @Test public void emptyOrderReachesPrecheckAndPriceNotCreate() throws Exception {
        List<String> actions=new ArrayList<>();
        Gateway.Transport fixture=(url,p)->{
            String action=p.getString("action"); actions.add(action);
            if(action.equals("user.tw.ride.check")) return ApiResponse.decode(action,200,"{\"code\":301,\"msg\":\"无骑行中订单\"}");
            if(action.equals("user.ride.pre.ride")) {
                assertEquals(1,p.getInt("openBluetooth")); assertTrue(p.getBoolean("bleStatus")); assertEquals(0,p.getInt("force"));
                return new JSONObject().put("result",true).put("causeType",1100).put("orderGuid","order-test").put("bikeType",0).put("bluetoothKey","secret-command-never-returned");
            }
            return new JSONObject().put("ridePriceInfo",new JSONObject().put("priceRule","前60分钟免费"));
        };
        JSONObject pre=new RideApi(session(),fixture,true).prepare("1234567890",new double[]{39.9,116.3});
        assertEquals(java.util.Arrays.asList("user.tw.ride.check","user.ride.pre.ride","tw.bike.unlock.page.basic.info"),actions);
        assertEquals("order-test",pre.getString("orderGuid")); assertFalse(pre.has("bluetoothKey"));
    }
    @Test public void ridingOrderBlocksNewPrecheck() throws Exception {
        int[] calls={0};
        RideApi api=new RideApi(session(),(url,p)->{ calls[0]++;return new JSONObject().put("rideInfo",new JSONObject().put("rideStatus",0)); },false);
        try { api.prepare("1234",new double[]{39.9,116.3}); fail(); } catch(IllegalStateException expected){}
        assertEquals(1,calls[0]);
    }
    @Test public void confirmationMatchesOfficialCreateFlags() throws Exception {
        RideApi api=new RideApi(session(),(url,p)->{
            assertEquals("user.ride.create",p.getString("action")); assertEquals("order-test",p.getString("orderGuid"));
            assertEquals(1,p.getInt("force")); assertEquals(1,p.getInt("rideLicenseforce")); assertFalse(p.getBoolean("connectBluetooth"));
            return new JSONObject().put("result",true).put("rideId","order-test");
        },true);
        api.create("1234","order-test",new double[]{39.9,116.3});
    }
    @Test public void anyServerClassifiedCampusBikeUsesNormalCreateMode() throws Exception {
        List<String> actions=new ArrayList<>();
        RideApi api=new RideApi(session(),(url,p)->{
            String action=p.getString("action"); actions.add(action);
            if(action.equals("user.tw.ride.check")) return ApiResponse.decode(action,200,"{\"code\":301,\"msg\":\"无骑行中订单\"}");
            if(action.equals("user.ride.pre.ride")) {
                assertEquals("any-campus-bike-number",p.getString("bikeNo"));
                assertEquals(0,p.getInt("mode"));
                return new JSONObject().put("result",true).put("causeType",1100).put("orderGuid","campus-order").put("bikeType",2);
            }
            if(action.equals("tw.bike.unlock.page.basic.info")) return new JSONObject().put("ridePriceInfo",new JSONObject().put("priceRule","校园计费规则"));
            assertEquals("user.ride.create",action);
            assertEquals(0,p.getInt("mode"));
            return new JSONObject().put("result",true).put("rideId","campus-order");
        },false);
        JSONObject pre=api.prepare("any-campus-bike-number",new double[]{39.9,116.3});
        assertTrue(pre.getBoolean("specialBike"));
        api.create("any-campus-bike-number",pre.getString("orderGuid"),new double[]{39.9,116.3});
        assertEquals(java.util.Arrays.asList("user.tw.ride.check","user.ride.pre.ride","tw.bike.unlock.page.basic.info","user.ride.create"),actions);
    }
    @Test public void closeReasonExplainsOutsideOperatingArea() throws Exception {
        JSONObject result=new JSONObject().put("status",2).put("causeType",1102);
        assertTrue(ApiResponse.closeReason(result).contains("运营区外"));
        assertTrue(ApiResponse.closeReason(result).contains("不能在此还车"));
    }
    @Test public void closeReasonDistinguishesPendingAndSuccess() throws Exception {
        assertTrue(ApiResponse.closeReason(new JSONObject().put("status",2).put("causeType",1101)).contains("正在校验"));
        assertTrue(ApiResponse.closeReason(new JSONObject().put("status",3).put("causeType",1103)).contains("校验通过"));
    }
    @Test public void serverMessagesRedactSensitiveValues() {
        String message=ApiResponse.clean("手机13800001234 token abcdef0123456789abcdef0123456789 https://example.org/?token=xyz");
        assertFalse(message.contains("13800001234")); assertFalse(message.contains("abcdef0123456789")); assertFalse(message.contains("token=xyz"));
    }
    @Test public void smsUsesOfficialActionAndOnlyExplicitCall() throws Exception {
        int[] calls={0}; LoginApi api=new LoginApi((url,p)->{
            calls[0]++; assertEquals("https://api.hellobike.com/api",url); assertEquals("user.account.sendCodeV3",p.getString("action"));
            assertEquals(0,p.getInt("source")); assertFalse(p.has("token")); return new JSONObject().put("ok",true);
        },"test-installation","test-app-signature");
        assertEquals(0,calls[0]); api.send("13800000000","010","110108"); assertEquals(1,calls[0]);
    }
    @Test public void smsChallengeIsNotReportedAsSent() throws Exception {
        LoginApi api=new LoginApi((url,p)->new JSONObject().put("ok",false).put("captchaType",2),"test","test");
        try { api.send("13800000000","010","110108"); fail(); }catch(ApiResponse.Failure e){ assertEquals(2,e.causeType); }
    }
    @Test public void loginUsesAuthGatewayAndMapsGuid() throws Exception {
        LoginApi api=new LoginApi((url,p)->{
            assertEquals("https://api.hellobike.com/auth",url); assertEquals("user.account.login",p.getString("action"));
            assertEquals("123456",p.getString("code")); assertEquals("test-app-signature",p.getString("apkSignHash"));
            return new JSONObject().put("token","test-token-long-enough").put("guid","test-user-guid-long-enough").put("ticket","new-ticket");
        },"test-installation","test-app-signature");
        JSONObject session=api.login("13800000000","123456","010","110108","test-login");
        assertEquals("test-user-guid-long-enough",session.getString("userGuid")); assertFalse(session.has("code")); assertFalse(session.has("mobile"));
    }
    @Test public void captchaTypeOneRequiresSliderEvenIfOkTrue() throws Exception {
        LoginApi api=new LoginApi((url,p)->new JSONObject().put("ok",true).put("captchaType",1),"test","test");
        try { api.send("13800000000","010","110108"); fail(); }catch(LoginApi.CaptchaRequired expected){}
    }
    @Test public void sliderSubmissionUsesUserSuppliedImagePixelOffset() throws Exception {
        LoginApi api=new LoginApi((url,p)->{
            assertEquals("user.account.sendCodeV3",p.getString("action")); assertEquals("87",p.getString("captcha"));
            return new JSONObject().put("ok",true);
        },"test","test");
        api.submitSlide("13800000000","010","110108",87);
    }
    @Test public void invalidPhoneAndCodeDoNotSendRequests() throws Exception {
        LoginApi api=new LoginApi((url,p)->{ fail("Unexpected request"); return null; },"test","test");
        try { api.send("bad","010","110108");fail(); }catch(IllegalArgumentException expected){}
        try { api.login("13800000000","x","010","110108","test");fail(); }catch(IllegalArgumentException expected){}
    }

    @Test public void regeoPayloadCarriesCoordinateAndReverseGeocodeAction() throws Exception {
        org.json.JSONObject payload=RegionApi.payload("PLJ110","Android 16",22.5308,113.9345,1789012805718L);
        assertEquals("hello.lbs.code.regeo",payload.getString("action"));
        assertEquals("reGeoCodingSearch",payload.getString("method"));
        org.json.JSONObject arg0=new org.json.JSONObject(payload.getString("arg0"));
        org.json.JSONObject location=arg0.getJSONArray("locations").getJSONObject(0);
        assertEquals(113.9345,location.getDouble("lng"),0.0000001);
        assertEquals(22.5308,location.getDouble("lat"),0.0000001);
        org.json.JSONObject arg1=payload.getJSONObject("arg1");
        assertEquals(RegionApi.APP_KEY,arg1.getString("appKey"));
        assertEquals(1789012805718L,arg1.getLong("timestamp"));
        assertTrue(arg1.getString("signature").length()==32);
    }
    @Test public void regeoParsesDistrictCodesFromRealShenzhenResponse() throws Exception {
        String body="{\"code\":0,\"data\":{\"status\":1,\"infoCode\":10000,\"info\":\"OK\",\"count\":0,\"data\":{\"reGeoCodesList\":[{\"formattedAddress\":\"\u5e7f\u4e1c\u7701\u6df1\u5733\u5e02\u5357\u5c71\u533a\",\"addressComponent\":{\"province\":\"\u5e7f\u4e1c\u7701\",\"city\":\"\u6df1\u5733\u5e02\",\"cityCode\":\"0755\",\"district\":\"\u5357\u5c71\u533a\",\"adCode\":\"440305\"}}]}}}";
        RegionApi.Region region=RegionApi.parse(new JSONObject(body));
        assertEquals("0755",region.cityCode);
        assertEquals("440305",region.adCode);
        assertEquals("广东省深圳市 南山区".substring(0,3),region.province);
        assertEquals("深圳市 南山区",region.label());
    }
    @Test public void regeoUsesProvinceLabelForMunicipalities() throws Exception {
        String body="{\"code\":0,\"data\":{\"status\":1,\"infoCode\":10000,\"data\":{\"reGeoCodesList\":[{\"addressComponent\":{\"province\":\"\u5317\u4eac\u5e02\",\"city\":\"\",\"cityCode\":\"010\",\"district\":\"\u6d77\u6dc0\u533a\",\"adCode\":\"110108\"}}]}}}";
        RegionApi.Region region=RegionApi.parse(new JSONObject(body));
        assertEquals("010",region.cityCode);
        assertEquals("110108",region.adCode);
        assertEquals("北京市 海淀区",region.label());
    }
    @Test public void regeoRejectionAndMissingAreaAreNotTreatedAsSuccess() throws Exception {
        try { RegionApi.parse(new JSONObject("{\"code\":1,\"data\":{\"status\":0,\"infoCode\":20003,\"count\":0,\"impl\":\"NONE\"}}")); fail(); }
        catch(ApiResponse.Failure e) { assertTrue(e.getMessage().contains("20003")); }
        try { RegionApi.parse(new JSONObject("{\"code\":0,\"data\":{\"status\":1,\"infoCode\":10000,\"data\":{\"reGeoCodesList\":[]}}}")); fail(); }
        catch(ApiResponse.Failure expected) {}
        try { RegionApi.parse(new JSONObject("{\"code\":0,\"data\":{\"status\":1,\"infoCode\":10000,\"data\":{\"reGeoCodesList\":[{\"addressComponent\":{\"cityCode\":\"\",\"adCode\":\"\"}}]}}}")); fail(); }
        catch(ApiResponse.Failure expected) {}
    }

    private RideFlow flow(RideState state,JSONObject session,Gateway.Transport transport,long[] uptime,long[] wall) {
        return new RideFlow(state,()->new RideApi(session,transport,false),()->new double[]{39.9,116.3},()->kotlin.Unit.INSTANCE,
            text->kotlin.Unit.INSTANCE,ms->{uptime[0]+=ms;return kotlin.Unit.INSTANCE;},()->wall[0],()->uptime[0]);
    }
    private Gateway.Transport rideTransport(List<String> actions) {
        boolean[] first={true};
        return (url,p)->{
            String action=p.getString("action"); actions.add(action);
            if(action.equals("user.tw.ride.check")) {
                if(first[0]) { first[0]=false; return ApiResponse.decode(action,200,"{\"code\":301,\"msg\":\"无骑行中订单\"}"); }
                return new JSONObject().put("rideInfo",new JSONObject().put("rideGuid","TWorder-1").put("bikeNo","9170939366").put("rideStatus",0));
            }
            if(action.equals("user.ride.pre.ride")) return new JSONObject().put("result",true).put("causeType",1100).put("orderGuid","order-1").put("bikeType",0);
            if(action.equals("tw.bike.unlock.page.basic.info")) return new JSONObject().put("ridePriceInfo",new JSONObject().put("priceRule","前1小时免费"));
            if(action.equals("user.ride.create")) { assertEquals("order-1",p.getString("orderGuid")); return new JSONObject().put("result",true).put("rideId","order-1"); }
            return new JSONObject().put("rideInfo",new JSONObject().put("rideGuid","TWorder-1").put("bikeNo","9170939366").put("rideStatus",0));
        };
    }
    @Test public void flowRunsPrecheckCreateAndRidePolling() throws Exception {
        RideState state=new RideState(); List<String> actions=new ArrayList<>();
        RideFlow flow=flow(state,session(),rideTransport(actions),new long[]{0},new long[]{1700000000000L});
        flow.prepare("9170939366");
        assertEquals("前1小时免费",flow.getPriceRule());
        assertTrue(flow.canOpen());
        flow.open();
        assertEquals(RideState.Stage.RIDING,state.stage);
        assertEquals("order-1",state.order);
        assertEquals(java.util.Arrays.asList("user.tw.ride.check","user.ride.pre.ride","tw.bike.unlock.page.basic.info","user.ride.create","user.tw.ride.check"),actions);
    }
    @Test public void flowRefusesOpenAfterPrecheckExpiry() throws Exception {
        RideState state=new RideState(); List<String> actions=new ArrayList<>(); long[] wall={1700000000000L};
        RideFlow flow=flow(state,session(),rideTransport(actions),new long[]{0},wall);
        flow.prepare("9170939366");
        wall[0]+=60001L;
        assertFalse(flow.canOpen());
        try { flow.open(); fail(); }catch(IllegalStateException expected){ assertTrue(expected.getMessage().contains("预校验已过期")); }
        assertFalse(actions.contains("user.ride.create"));
    }
    @Test public void flowCloseRunsLocationCheckThenConfirmsEnd() throws Exception {
        RideState state=new RideState(); state.stage=RideState.Stage.RIDING; state.order="order-1"; state.bike="9170939366";
        List<String> actions=new ArrayList<>(); long[] uptime={0};
        Gateway.Transport transport=(url,p)->{
            String action=p.getString("action"); actions.add(action);
            if(action.equals("ride.hub.pre.close")) {
                if(p.getInt("operateType")==0) return new JSONObject().put("status",3).put("causeType",1103).put("penaltyFree",1);
                assertEquals(1,p.getInt("poll"));
                return new JSONObject().put("status",3).put("causeType",1103);
            }
            if(action.equals("user.tw.ride.check")) return ApiResponse.decode(action,200,"{\"code\":301}");
            if(action.equals("user.ride.order.status")) return new JSONObject().put("rideStatus",30);
            return new JSONObject().put("tradeRegionInfo",new JSONObject().put("orderStatus",2).put("orderInfo",new JSONObject().put("payAmount","1.5")))
                .put("rideRegionInfo",new JSONObject().put("rideDuration",120).put("rideDistance","0.8"));
        };
        RideFlow flow=flow(state,session(),transport,uptime,new long[]{0});
        flow.checkClose();
        assertTrue(flow.closeCheckFresh());
        flow.commitClose();
        assertEquals(RideState.Stage.ENDED,state.stage);
        assertFalse(flow.closeCheckFresh());
        assertEquals(java.util.Arrays.asList("ride.hub.pre.close","ride.hub.pre.close","user.tw.ride.check","user.ride.order.status","tw.end.module.info"),actions);
    }
    @Test public void flowCloseCheckRejectsOutsideOperatingAreaWithoutCommitting() throws Exception {
        RideState state=new RideState(); state.stage=RideState.Stage.RIDING; state.order="order-1"; state.bike="9170939366";
        List<String> actions=new ArrayList<>();
        Gateway.Transport transport=(url,p)->{ actions.add(p.getString("action")); return new JSONObject().put("status",2).put("causeType",1102); };
        RideFlow flow=flow(state,session(),transport,new long[]{0},new long[]{0});
        try { flow.checkClose(); fail(); }catch(ApiResponse.Failure e){ assertTrue(e.getMessage().contains("运营区外")); }
        assertFalse(flow.closeCheckFresh());
        assertEquals(RideState.Stage.RIDING,state.stage);
        assertEquals(java.util.Arrays.asList("ride.hub.pre.close"),actions);
    }
    @Test public void flowRefreshRejectsOrderMismatch() throws Exception {
        RideState state=new RideState(); state.stage=RideState.Stage.RIDING; state.order="order-1"; state.bike="9170939366";
        Gateway.Transport transport=(url,p)->new JSONObject().put("rideInfo",new JSONObject().put("rideGuid","other-order").put("rideStatus",0));
        RideFlow flow=flow(state,session(),transport,new long[]{0},new long[]{0});
        try { flow.refresh(); fail(); }catch(IllegalStateException expected){ assertTrue(expected.getMessage().contains("不一致")); }
        assertEquals("order-1",state.order);
    }

    @Test public void tokenSessionAcceptsKeyValuePaste() throws Exception {
        JSONObject session=TokenSession.INSTANCE.parse("token=0123456789abcdef0123\nticket=ticket-value\nuserGuid=abcdef0123456789abcd\n",null,"010","110108");
        assertEquals("0123456789abcdef0123",session.getString("token"));
        assertEquals("ticket-value",session.getString("ticket"));
        assertEquals("abcdef0123456789abcd",session.getString("userGuid"));
        assertEquals("62",session.getString("systemCode"));
    }
    @Test public void tokenSessionReusesStoredIdentityAndRegionForBareToken() throws Exception {
        JSONObject previous=new JSONObject().put("userGuid","previous-user-guid-1234").put("ticket","stored-ticket").put("cityCode","0755").put("adCode","440305");
        JSONObject session=TokenSession.INSTANCE.parse("fresh-token-0123456789ab",previous,"","");
        assertEquals("fresh-token-0123456789ab",session.getString("token"));
        assertEquals("previous-user-guid-1234",session.getString("userGuid"));
        assertEquals("stored-ticket",session.getString("ticket"));
        assertEquals("0755",session.getString("cityCode"));
        assertEquals("440305",session.getString("adCode"));
    }
    @Test public void tokenSessionFallsBackToResolvedRegionAndRejectsUnknownIdentity() throws Exception {
        JSONObject identity=new JSONObject().put("userGuid","previous-user-guid-1234").put("cityCode","").put("adCode","");
        JSONObject session=TokenSession.INSTANCE.parse("fresh-token-0123456789ab",identity,"010","110108");
        assertEquals("010",session.getString("cityCode"));
        assertEquals("110108",session.getString("adCode"));
        try { TokenSession.INSTANCE.parse("fresh-token-0123456789ab",null,"",""); fail(); }
        catch(IllegalArgumentException e){ assertTrue(e.getMessage().contains("用户号")); }
        try { TokenSession.INSTANCE.parse("short",null,"",""); fail(); }
        catch(IllegalArgumentException e){ assertTrue(e.getMessage().contains("token 无效")); }
        try { TokenSession.INSTANCE.parse("",null,"010","110108"); fail(); }
        catch(IllegalArgumentException expected){}
    }
}
