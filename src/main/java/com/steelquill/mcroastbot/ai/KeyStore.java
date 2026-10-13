package com.steelquill.mcroastbot.ai;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * API key storage with AES-256-GCM at rest, round-robin rotation, and
 * failover classification. Pure JDK, no Bukkit. Persist via exportBlobs()
 * into keys.yml; the raw key is NEVER logged - only masked tails.
 */
public final class KeyStore {
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    public record Entry(String id, String providerId, String blob, boolean disabled,
                        String disabledReason, int failCount) {
    }

    private final byte[] kek;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Integer> cursor = new LinkedHashMap<>();
    private int seq;

    public KeyStore(byte[] kek) {
        if (kek == null || kek.length != 32) {
            throw new IllegalArgumentException("KEK must be 32 bytes");
        }
        this.kek = kek.clone();
    }

    public synchronized String addKey(String providerId, String rawKey) throws GeneralSecurityException {
        String id = providerId + ":" + (++seq);
        entries.put(id, new Entry(id, providerId, encrypt(rawKey), false, "", 0));
        return id;
    }

    public synchronized void importBlob(String id, String providerId, String blob,
                                        boolean disabled, String reason) {
        entries.put(id, new Entry(id, providerId, blob, disabled, reason == null ? "" : reason, 0));
        try {
            int n = Integer.parseInt(id.substring(id.lastIndexOf(':') + 1));
            seq = Math.max(seq, n);
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
        }
    }

    /** Next usable key for a provider (round-robin). Null if none. */
    public synchronized Entry next(String providerId) {
        List<Entry> usable = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.providerId().equals(providerId) && !e.disabled()) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        int i = Math.floorMod(cursor.getOrDefault(providerId, 0), usable.size());
        cursor.put(providerId, i + 1);
        return usable.get(i);
    }

    public synchronized String decrypt(Entry e) throws GeneralSecurityException {
        byte[] all = Base64.getDecoder().decode(e.blob());
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(all, 0, iv, 0, IV_BYTES);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] pt = c.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        return new String(pt, StandardCharsets.UTF_8);
    }

    /** Rate limit / overload / 5xx: rotate, keep key enabled. */
    public synchronized void markTransientFailure(String id) {
        Entry e = entries.get(id);
        if (e != null) {
            entries.put(id, new Entry(e.id(), e.providerId(), e.blob(), e.disabled(),
                    e.disabledReason(), e.failCount() + 1));
        }
    }

    /** 401 invalid key: disable until an OP re-adds it. */
    public synchronized void markInvalid(String id) {
        Entry e = entries.get(id);
        if (e != null) {
            entries.put(id, new Entry(e.id(), e.providerId(), e.blob(), true, "401 unauthorized", e.failCount()));
        }
    }

    public synchronized Map<String, Entry> snapshot() {
        return new LinkedHashMap<>(entries);
    }

    public synchronized int enabledCount(String providerId) {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.providerId().equals(providerId) && !e.disabled()) {
                n++;
            }
        }
        return n;
    }

    public static String mask(String raw) {
        if (raw == null || raw.length() <= 4) {
            return "****";
        }
        return "****" + raw.substring(raw.length() - 4);
    }

    private String encrypt(String raw) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = c.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[IV_BYTES + ct.length];
        System.arraycopy(iv, 0, all, 0, IV_BYTES);
        System.arraycopy(ct, 0, all, IV_BYTES, ct.length);
        return Base64.getEncoder().encodeToString(all);
    }
}
