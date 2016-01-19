/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.cts.managedprofile;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserManager;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PhoneAccountTest extends InstrumentationTestCase {

    static final String CALL_PROVIDER_ID = "testapps_TestConnectionService_CALL_PROVIDER_ID";

    private static final String COMMAND_ENABLE = "telecom set-phone-account-enabled";

    private static final String PHONE_NUMBER = "886";

    private static final String QUERY_CALL_THROUGH_OUR_CONNECTION_SERVICE = Calls.NUMBER
            + " = ? AND " + Calls.PHONE_ACCOUNT_COMPONENT_NAME + " = ?";

    private TelecomManager mTelecomManager;

    private Context mContext;

    private Instrumentation mInstrumentation;

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";

    private static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE = new PhoneAccountHandle(
            new ComponentName(MANAGED_PROFILE_PKG,
                    DummyConnectionService.class.getName()), CALL_PROVIDER_ID,
            Process.myUserHandle());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = getInstrumentation().getContext();
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     *  Placing an outgoing call through our phone account and verify the call is inserted
     *  properly.
     */
    public void testOutgoingCall() throws Exception {
        // Make sure no lingering values from previous runs.
        cleanupCall(false);
        final Context context = getInstrumentation().getContext();
        final HandlerThread handlerThread = new HandlerThread("Observer");
        // Register the phone account.
        final PhoneAccountHandle phoneAccountHandle = registerPhoneAccount().getAccountHandle();
        try {
            // Register the ContentObserver so that we will be get notified when the call is
            // inserted.
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            handlerThread.start();
            context.getContentResolver().registerContentObserver(Calls.CONTENT_URI, false,
                    new CalllogContentObserver(new Handler(handlerThread.getLooper()),
                            countDownLatch));

            // Place the call.
            Uri phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, PHONE_NUMBER, null);
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
            mTelecomManager.placeCall(phoneUri, extras);

            // Make sure the call inserted is correct.
            boolean calllogProviderChanged = countDownLatch.await(1, TimeUnit.MINUTES);
            assertTrue(calllogProviderChanged);
            assertCalllogInserted(Calls.OUTGOING_TYPE);
        } finally {
            handlerThread.quit();
            cleanupCall(true /* verifyDeletion */ );
            unregisterPhoneAccount();
        }
    }

    /**
     *  Add an incoming call with our phone account and verify the call is inserted properly.
     */
    public void testIncomingCall() throws Exception {
        // Make sure no lingering values from previous runs.
        cleanupCall(false);
        final Context context = getInstrumentation().getContext();
        final HandlerThread handlerThread = new HandlerThread("Observer");
        // Register the phone account.
        final PhoneAccountHandle phoneAccountHandle = registerPhoneAccount().getAccountHandle();
        try {
            // Register the ContentObserver so that we will be get notified when the call is
            // inserted.
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            handlerThread.start();
            context.getContentResolver().registerContentObserver(Calls.CONTENT_URI, false,
                    new CalllogContentObserver(new Handler(handlerThread.getLooper()),
                            countDownLatch));

            // Add a incoming call.
            final Bundle bundle = new Bundle();
            final Uri phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, PHONE_NUMBER, null);
            bundle.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, phoneUri);
            mTelecomManager.addNewIncomingCall(phoneAccountHandle, bundle);

            // Make sure the call inserted is correct.
            boolean calllogProviderChanged = countDownLatch.await(1, TimeUnit.MINUTES);
            assertTrue(calllogProviderChanged);
            assertCalllogInserted(Calls.INCOMING_TYPE);
        } finally {
            handlerThread.quit();
            cleanupCall(true /* verifyDeletion */ );
            unregisterPhoneAccount();
        }
    }

    public void testEnsureCallNotInserted() {
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver()
                    .query(Calls.CONTENT_URI, null, Calls.NUMBER + " = ?",
                            new String[]{PHONE_NUMBER}, null);
            assertEquals(0, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testRegisterPhoneAccount() throws Exception {
        registerPhoneAccount();
    }

    public void testUnregisterPhoneAccount() {
        unregisterPhoneAccount();
    }

    public void testPhoneAccountNotRegistered() {
        assertNull(mTelecomManager.getPhoneAccount(PHONE_ACCOUNT_HANDLE));
    }

    private void assertCalllogInserted(int type) {
        Cursor cursor = null;
        try {
            final String connectionServiceComponentName = new ComponentName(mContext,
                    DummyConnectionService.class).flattenToString();
            cursor = mContext.getContentResolver()
                    .query(Calls.CONTENT_URI, null,
                            QUERY_CALL_THROUGH_OUR_CONNECTION_SERVICE + " AND " +
                                    Calls.TYPE + " = ?",
                            new String[]{PHONE_NUMBER, connectionServiceComponentName,
                                    String.valueOf(type)}, null);
            assertEquals(1, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void cleanupCall(boolean verifyDeletion) {
        final String connectionServiceComponentName = new ComponentName(mContext,
                DummyConnectionService.class).flattenToString();
        int numRowDeleted = mContext.getContentResolver()
                .delete(Calls.CONTENT_URI, QUERY_CALL_THROUGH_OUR_CONNECTION_SERVICE,
                        new String[]{PHONE_NUMBER, connectionServiceComponentName});
        if (verifyDeletion) {
            assertEquals(1, numRowDeleted);
        }
    }

    private PhoneAccount registerPhoneAccount() throws Exception {
        final PhoneAccount phoneAccount = PhoneAccount.builder(
                PHONE_ACCOUNT_HANDLE,
                "TelecomTestApp Call Provider")
                .setAddress(Uri.parse("tel:555-TEST"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setShortDescription("a short description for the call provider")
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                .build();
        mTelecomManager.registerPhoneAccount(phoneAccount);
        enablePhoneAccount(PHONE_ACCOUNT_HANDLE);
        // make sure the registration is successful.
        assertNotNull(mTelecomManager.getPhoneAccount(PHONE_ACCOUNT_HANDLE));
        return phoneAccount;
    }

    private void unregisterPhoneAccount() {
        mTelecomManager.unregisterPhoneAccount(PHONE_ACCOUNT_HANDLE);
        assertNull(mTelecomManager.getPhoneAccount(PHONE_ACCOUNT_HANDLE));
    }

    /**
     * Running adb command to enable phone account.
     */
    private void enablePhoneAccount(PhoneAccountHandle handle) throws Exception {
        final ComponentName component = handle.getComponentName();
        final UserManager userManager = (UserManager) mContext.getSystemService(
                Context.USER_SERVICE);
        executeShellCommand(COMMAND_ENABLE + " "
                + component.getPackageName() + "/" + component.getClassName() + " "
                + handle.getId() + " " + userManager
                .getSerialNumberForUser(Process.myUserHandle()));
    }

    private String executeShellCommand(String command) throws Exception {
        final ParcelFileDescriptor pfd =
                mInstrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                br.close();
            }
            pfd.close();
        }
    }

    /**
     * Observe the change of calllog provider.
     */
    private class CalllogContentObserver extends ContentObserver {

        private final CountDownLatch mCountDownLatch;

        public CalllogContentObserver(Handler handler, final CountDownLatch countDownLatch) {
            super(handler);
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            mCountDownLatch.countDown();
        }
    }
}
