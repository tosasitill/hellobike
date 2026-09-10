package dev.local.ridecompact;

import org.junit.Test;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class CoreTest {
    @Test public void parsesOfficialNCode() { assertEquals("1234567890",ScanParser.parse("https://c3x.me/?n=1234567890").bikeNo); }
    @Test public void parsesHttpCodeWithoutOpeningIt() { assertEquals("1234567890",ScanParser.parse("http://c3x.me/?n=1234567890").bikeNo); }
    @Test public void parsesManualCode() { assertEquals("1234567890",ScanParser.parse(" 1234567890 ").bikeNo); }
    @Test public void decodesPercentEncodedCode() { assertEquals("1234",ScanParser.parse("https://c3x.me/?n=%31%32%33%34").bikeNo); }
    @Test public void rejectsUntrustedAndAmbiguousCodes() {
        for(String value:new String[]{"https://c3x.me.evil/?n=1234","https://evil/?n=1234","javascript:1234","https://c3x.me/?n=1234&n=5678","https://c3x.me/?m=1234","https://c3x.me/?n=1234&e=5678","https://user@c3x.me/?n=1234","https://c3x.me:443/?n=1234","https://c3x.me/?n=1","https://c3x.me/?n=%2F1234"}) {
            try { ScanParser.parse(value); fail("Accepted unsupported code"); } catch(IllegalArgumentException expected){}
        }
    }
    @Test public void openingIsSingleUse() {
        RideState state=new RideState(); state.prepared("1234","order",1000); assertTrue(state.canOpen(2000));
        state.opening(2000); assertFalse(state.canOpen(2001)); assertFalse(state.canPrepare()); assertFalse(state.canClose());
    }
    @Test public void expiredOrClockRewoundPrecheckCannotOpen() {
        RideState state=new RideState(); state.prepared("1234","order",1000);
        assertFalse(state.canOpen(999)); assertFalse(state.canOpen(61000));
    }
    @Test public void closingCannotBeResentWhileRideStillActive() {
        RideState state=new RideState(); state.prepared("1234","order",0); state.observed(0); assertTrue(state.canClose());
        state.closing(); state.observed(0); assertEquals(RideState.Stage.CLOSING,state.stage); assertFalse(state.canClose());
        state.observed(2); assertTrue(state.canPrepare()); assertFalse(state.canClose());
    }
    @Test public void unknownStatusBlocksMutations() {
        RideState state=new RideState(); state.prepared("1234","order",0); state.observed(99);
        assertFalse(state.canOpen(1)); assertFalse(state.canPrepare()); assertFalse(state.canClose());
    }
    @Test public void outsideChinaCoordinatesRemainUnchanged() {
        assertArrayEquals(new double[]{40,-74},Coordinates.toGcj02(40,-74),0.00000001);
    }
    @Test public void knownCoordinateTransform() {
        assertArrayEquals(new double[]{39.9102265,116.4037136},Coordinates.toGcj02(39.908823,116.397470),0.000002);
    }
    @Test public void importsActualHarWithoutLeakingCredentials() throws Exception {
        Path har=Path.of("../../../apmgw.hellobike.com_2026_09_07_11_42_00.har");
        if(!Files.exists(har)) har=Path.of("../../apmgw.hellobike.com_2026_09_07_11_42_00.har");
        JSONObject result=AccountImporter.parse(new String(Files.readAllBytes(har),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(32,result.getString("token").length()); assertEquals(32,result.getString("userGuid").length());
        assertEquals("6.99.71",result.getString("version")); assertEquals("7.0.30",result.getString("h5Version"));
        assertFalse(result.getString("ticket").isEmpty()); assertFalse(result.has("bluetoothKey")); assertFalse(result.has("orderGuid"));
    }
    @Test public void rejectsEmptyHar() throws Exception {
        try { AccountImporter.parse("{\"log\":{\"entries\":[]}}"); fail(); } catch(IllegalArgumentException expected){}
    }
    @Test public void acceptsSessionJson() throws Exception {
        JSONObject input=new JSONObject().put("token","test-only-not-a-real-session");
        assertEquals(input.toString(),AccountImporter.parse(input.toString()).toString());
    }
}
