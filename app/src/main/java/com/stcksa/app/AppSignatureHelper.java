package com.stcksa.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppSignatureHelper extends ContextWrapper {

    private static final String TAG = "AppSignatureHelper";
    private static final String HASH_TYPE = "SHA-256";
    private static final int NUM_HASHED_BYTES = 9;
    private static final int NUM_BASE64_CHAR = 11;

    public AppSignatureHelper(Context context) {
        super(context);
    }

    public List<String> getAppSignatures() {
        List<String> appSignatures = new ArrayList<>();
        try {
            String packageName = getPackageName();
            PackageManager packageManager = getPackageManager();

            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                );
                SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo == null) {
                    return appSignatures;
                }
                signatures = signingInfo.hasMultipleSigners()
                        ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
            } else {
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                );
                signatures = packageInfo.signatures;
            }

            if (signatures == null) {
                return appSignatures;
            }

            for (Signature signature : signatures) {
                String hash = hash(packageName, signature.toCharsString());
                if (hash != null) {
                    appSignatures.add(hash);
                    Log.d(TAG, "App hash generated: " + hash);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to find package to generate hash.", e);
        }
        return appSignatures;
    }

    private static String hash(String packageName, String signature) {
        String appInfo = packageName + " " + signature;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_TYPE);
            messageDigest.update(appInfo.getBytes(StandardCharsets.UTF_8));

            byte[] hashSignature = Arrays.copyOfRange(messageDigest.digest(), 0, NUM_HASHED_BYTES);
            String base64Hash = Base64.encodeToString(
                    hashSignature,
                    Base64.NO_PADDING | Base64.NO_WRAP
            );
            return base64Hash.substring(0, NUM_BASE64_CHAR);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "NoSuchAlgorithm: " + HASH_TYPE, e);
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate app hash.", e);
        }
        return null;
    }
}
