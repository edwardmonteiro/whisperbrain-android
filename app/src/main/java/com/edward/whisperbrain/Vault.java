package com.edward.whisperbrain;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;

/** Encrypted local user configuration. No key, memory, or audio is shipped in the APK. */
public final class Vault {
    private static final String ALIAS = "whisperbrain.personal.v1";
    private final SharedPreferences prefs;
    public Vault(Context context) { prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return gen.generateKey();
    }
    public synchronized void put(String name, String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        c.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        String encrypted = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString(name, encrypted).commit()) throw new Exception("Storage write failed");
    }
    public synchronized String get(String name, String fallback) throws Exception {
        String value = prefs.getString(name, null);
        if (value == null) return fallback;
        String[] parts = value.split(":", 2);
        if (parts.length != 2) throw new Exception("Invalid vault entry");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        c.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    public synchronized JSONArray memories() throws Exception { return new JSONArray(get("memories", "[]")); }
    public synchronized void remember(String text) throws Exception {
        JSONArray notes = memories();
        if (notes.length() >= 20) throw new Exception("Memory is full. Remove a note first.");
        String note = text.trim();
        if (note.isEmpty() || note.length() > 400) throw new Exception("Use 1–400 characters.");
        notes.put(note);
        put("memories", notes.toString());
    }
    public synchronized void deleteMemory(int index) throws Exception {
        JSONArray notes = memories(); notes.remove(index); put("memories", notes.toString());
    }
    public synchronized void forgetKey() { prefs.edit().remove("api_key").apply(); }
}
