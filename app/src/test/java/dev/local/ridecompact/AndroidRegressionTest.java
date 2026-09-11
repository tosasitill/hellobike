package dev.local.ridecompact;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.ComponentName;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.widget.ImageButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class AndroidRegressionTest {
    private JSONObject account() throws Exception {
        return new JSONObject().put("token","test-token-not-a-real-account").put("userGuid","test-user-guid-not-real")
            .put("systemCode","62").put("version","6.99.71").put("h5Version","7.0.30").put("ticket","test")
            .put("cityCode","010").put("adCode","110108");
    }
    static final class TestKeys implements SessionStore.Keys {
        final Map<String,SecretKey> values=new HashMap<>();
        String invalid=""; int reads=0;
        public SecretKey get(String alias,boolean create) throws Exception {
            reads++;
            if(alias.equals(invalid)) throw new KeyPermanentlyInvalidatedException();
            if(create) { KeyGenerator gen=KeyGenerator.getInstance("AES"); gen.init(256); values.put(alias,gen.generateKey()); }
            if(!values.containsKey(alias)) throw new java.security.UnrecoverableKeyException();
            return values.get(alias);
        }
        public void remove(String alias) { values.remove(alias); }
    }
    @Test public void invalidatedKeyCanBeReplacedWithoutDecryptingOldBlob() throws Exception {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("vault-test",0);
        TestKeys keys=new TestKeys(); SessionStore first=new SessionStore(prefs,keys); first.save(account());
        String old=prefs.getString("alias",""); keys.invalid=old;
        SessionStore afterRestart=new SessionStore(prefs,keys);
        try { afterRestart.load(); fail(); }catch(SessionStore.RecoveryRequired expected){}
        int reads=keys.reads;
        assertNull(afterRestart.forReplacement()); assertEquals(reads,keys.reads);
        afterRestart.save(account()); assertNotEquals(old,prefs.getString("alias",""));
        assertEquals("test-token-not-a-real-account",new SessionStore(prefs,keys).load().getString("token"));
        assertFalse(keys.values.containsKey(old));
    }
    @Test public void repeatedUiReadsDoNotRepeatedlyTouchKeyStore() throws Exception {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("cache-test",0);
        TestKeys keys=new TestKeys(); SessionStore store=new SessionStore(prefs,keys); store.save(account()); int reads=keys.reads;
        for(int i=0;i<20;i++) assertNotNull(store.load()); assertEquals(reads,keys.reads);
        assertFalse(prefs.getString("data","").contains(account().getString("token")));
    }
    @Test public void missingKeyIsNotSilentlyRegeneratedForOldCiphertext() throws Exception {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("missing-test",0);
        TestKeys keys=new TestKeys(); SessionStore store=new SessionStore(prefs,keys); store.save(account()); keys.values.clear();
        try { new SessionStore(prefs,keys).load(); fail(); }catch(SessionStore.RecoveryRequired expected){}
        assertTrue(keys.values.isEmpty());
    }
    @Test public void corruptedCiphertextCanBeReimported() throws Exception {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("corrupt-test",0);
        TestKeys keys=new TestKeys(); SessionStore store=new SessionStore(prefs,keys); store.save(account());
        prefs.edit().putString("data","broken").commit(); SessionStore broken=new SessionStore(prefs,keys);
        assertNull(broken.forReplacement()); broken.save(account()); assertNotNull(broken.load());
    }
    @Test public void mainAndLoginActivitiesStartWithoutAccountsOrNetwork() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) { assertNotNull(controller.get().findViewById(android.R.id.content)); }
        try(var controller=Robolectric.buildActivity(LoginActivity.class).setup()) { assertNotNull(controller.get().findViewById(android.R.id.content)); }
    }
    @Test public void scannerIsPrivatePortraitAndHasTorchControl() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        ActivityInfo info=context.getPackageManager().getActivityInfo(new ComponentName(context,PortraitScanActivity.class),0);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,info.screenOrientation); assertFalse(info.exported);
        try(var controller=Robolectric.buildActivity(PortraitScanActivity.class).create()) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,controller.get().getRequestedOrientation());
            ImageButton torch=controller.get().findViewById(R.id.scan_torch); assertNotNull(torch);
            assertEquals("打开手电筒",torch.getContentDescription().toString());
        }
    }
}
