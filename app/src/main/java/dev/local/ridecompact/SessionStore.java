package dev.local.ridecompact;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SessionStore {
    static final class RecoveryRequired extends IllegalStateException {
        RecoveryRequired() { super("本地加密密钥已失效。请重新导入账号或短信登录，新的凭据会使用新密钥保存；骑行订单不会被清除。"); }
    }
    interface Keys {
        SecretKey get(String alias,boolean create) throws Exception;
        void remove(String alias) throws Exception;
    }
    private static final String LEGACY="ridecompact.session.v1";
    private final SharedPreferences preferences;
    private final Keys keys;
    private String cachedBlob;
    private JSONObject cached;
    private boolean failed=false;
    SessionStore(Context context) { this(context.getSharedPreferences("session",0),new AndroidKeys()); }
    SessionStore(SharedPreferences preferences,Keys keys) { this.preferences=preferences; this.keys=keys; }
    private static final class AndroidKeys implements Keys {
        public SecretKey get(String alias,boolean create) throws Exception {
            KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
            if(!store.containsAlias(alias)) {
                if(!create) throw new java.security.UnrecoverableKeyException("Missing account key");
                KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false)
                    .setInvalidatedByBiometricEnrollment(false).build());
                return generator.generateKey();
            }
            return (SecretKey)store.getKey(alias,null);
        }
        public void remove(String alias) throws Exception {
            KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(alias);
        }
    }
    static JSONObject validate(JSONObject input) throws Exception {
        JSONObject out=new JSONObject();
        for(String field:new String[]{"token","ticket","userGuid","systemCode","version","h5Version","cityCode","adCode"}) {
            String value=input.optString(field,"");
            if(value.length()>2048 || value.contains("\n") || value.contains("\r")) throw new IllegalArgumentException("账号文件字段无效");
            out.put(field,value);
        }
        if(out.getString("token").length()<16 || out.getString("userGuid").length()<16) throw new IllegalArgumentException("账号缺少有效 token 或 userGuid");
        if(!out.getString("adCode").matches("[0-9]{6}") || !out.getString("cityCode").matches("[0-9]{3,6}")) throw new IllegalArgumentException("账号缺少城市编码");
        if(!"62".equals(out.getString("systemCode"))) throw new IllegalArgumentException("账号 systemCode 不匹配");
        return out;
    }
    synchronized void save(JSONObject input) throws Exception {
        JSONObject data=validate(input);
        String oldAlias=preferences.getString("alias",LEGACY);
        String alias="ridecompact.session.v2."+UUID.randomUUID();
        boolean committed=false;
        try {
            SecretKey key=keys.get(alias,true);
            Cipher encrypt=Cipher.getInstance("AES/GCM/NoPadding"); encrypt.init(Cipher.ENCRYPT_MODE,key);
            byte[] plain=data.toString().getBytes(StandardCharsets.UTF_8),encrypted=encrypt.doFinal(plain),iv=encrypt.getIV();
            Cipher verify=Cipher.getInstance("AES/GCM/NoPadding");
            verify.init(Cipher.DECRYPT_MODE,keys.get(alias,false),new GCMParameterSpec(128,iv));
            if(!java.util.Arrays.equals(plain,verify.doFinal(encrypted))) throw new java.security.GeneralSecurityException("Key self-check failed");
            String blob=Base64.encodeToString(encrypted,Base64.NO_WRAP);
            committed=preferences.edit().putString("alias",alias).putString("iv",Base64.encodeToString(iv,Base64.NO_WRAP)).putString("data",blob).commit();
            if(!committed) throw new IllegalStateException("账号保存失败，原凭据未被替换");
            cached=data; cachedBlob=blob; failed=false;
            try { keys.remove(oldAlias); } catch(Exception ignored) {}
        } finally {
            if(!committed) try { keys.remove(alias); } catch(Exception ignored) {}
        }
    }
    synchronized JSONObject load() throws Exception {
        String blob=preferences.getString("data",null);
        if(blob==null) { cached=null; cachedBlob=null; failed=false; return null; }
        if(blob.equals(cachedBlob)) {
            if(failed) throw new RecoveryRequired();
            if(cached!=null) return new JSONObject(cached.toString());
        }
        try {
            String alias=preferences.getString("alias",LEGACY);
            byte[] iv=Base64.decode(preferences.getString("iv",""),Base64.NO_WRAP);
            Cipher decrypt=Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE,keys.get(alias,false),new GCMParameterSpec(128,iv));
            cached=validate(new JSONObject(new String(decrypt.doFinal(Base64.decode(blob,Base64.NO_WRAP)),StandardCharsets.UTF_8)));
            cachedBlob=blob; failed=false;
            return new JSONObject(cached.toString());
        } catch(java.security.GeneralSecurityException | java.security.ProviderException | IllegalArgumentException | org.json.JSONException e) {
            cached=null; cachedBlob=blob; failed=true; throw new RecoveryRequired();
        }
    }
    synchronized JSONObject forReplacement() throws Exception {
        try { return load(); } catch(RecoveryRequired expected) { return null; }
    }
    synchronized void clear() throws Exception {
        String alias=preferences.getString("alias",LEGACY);
        if(!preferences.edit().clear().commit()) throw new IllegalStateException("清除失败");
        cached=null; cachedBlob=null; failed=false;
        keys.remove(alias);
    }
}
