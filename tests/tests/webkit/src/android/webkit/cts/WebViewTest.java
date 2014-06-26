/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.cts.util.EvaluateJsResultPollingCheck;
import android.cts.util.PollingCheck;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.SystemClock;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ClientCertRequest;
import android.webkit.ConsoleMessage;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebView.PictureListener;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.webkit.cts.WebViewOnUiThread.WaitForLoadedClient;
import android.webkit.cts.WebViewOnUiThread.WaitForProgressClient;
import android.widget.LinearLayout;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Principal;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

public class WebViewTest extends ActivityInstrumentationTestCase2<WebViewStubActivity> {
    private static final String LOGTAG = "WebViewTest";
    private static final int INITIAL_PROGRESS = 100;
    private static long TEST_TIMEOUT = 20000L;
    private static final String X_REQUESTED_WITH = "X-Requested-With";
    private static final String PRINTER_TEST_FILE = "print.pdf";
    private static final String PDF_PREAMBLE = "%PDF-1";

    /**
     * This is the minimum number of milliseconds to wait for scrolling to
     * start. If no scrolling has started before this timeout then it is
     * assumed that no scrolling will happen.
     */
    private static final long MIN_SCROLL_WAIT_MS = 1000;
    /**
     * Once scrolling has started, this is the interval that scrolling
     * is checked to see if there is a change. If no scrolling change
     * has happened in the given time then it is assumed that scrolling
     * has stopped.
     */
    private static final long SCROLL_WAIT_INTERVAL_MS = 200;

    private WebView mWebView;
    private CtsTestServer mWebServer;
    private WebViewOnUiThread mOnUiThread;
    private WebIconDatabase mIconDb;

    /**
     * Taken verbatim from AndroidKeyStoreTest.java. Copying the build notes here for reference.
     * The keys and certificates below are generated with:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     * openssl req -newkey rsa:1024 -keyout userkey.pem -nodes -days 3650 -out userkey.req
     * mkdir -p demoCA/newcerts
     * touch demoCA/index.txt
     * echo "01" > demoCA/serial
     * openssl ca -out usercert.pem -in userkey.req -cert cacert.pem -keyfile cakey.pem -days 3650
     */

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in usercert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_USER_1 = new byte[] {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x95, (byte) 0x30, (byte) 0x82,
            (byte) 0x01, (byte) 0xfe, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x30, (byte) 0x0d,
            (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
            (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x05,
            (byte) 0x00, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06,
            (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31,
            (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d, (byte) 0x6f,
            (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69, (byte) 0x6e,
            (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77, (byte) 0x31,
            (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e,
            (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20,
            (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43,
            (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x30, (byte) 0x1e,
            (byte) 0x17, (byte) 0x0d, (byte) 0x31, (byte) 0x32, (byte) 0x30, (byte) 0x38,
            (byte) 0x31, (byte) 0x34, (byte) 0x32, (byte) 0x33, (byte) 0x32, (byte) 0x35,
            (byte) 0x34, (byte) 0x38, (byte) 0x5a, (byte) 0x17, (byte) 0x0d, (byte) 0x32,
            (byte) 0x32, (byte) 0x30, (byte) 0x38, (byte) 0x31, (byte) 0x32, (byte) 0x32,
            (byte) 0x33, (byte) 0x32, (byte) 0x35, (byte) 0x34, (byte) 0x38, (byte) 0x5a,
            (byte) 0x30, (byte) 0x55, (byte) 0x31, (byte) 0x0b, (byte) 0x30, (byte) 0x09,
            (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06, (byte) 0x13,
            (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x08,
            (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31, (byte) 0x1b,
            (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e, (byte) 0x64,
            (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20, (byte) 0x54,
            (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43, (byte) 0x61,
            (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x31, (byte) 0x1c, (byte) 0x30,
            (byte) 0x1a, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x03,
            (byte) 0x13, (byte) 0x13, (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76,
            (byte) 0x65, (byte) 0x72, (byte) 0x31, (byte) 0x2e, (byte) 0x65, (byte) 0x78,
            (byte) 0x61, (byte) 0x6d, (byte) 0x70, (byte) 0x6c, (byte) 0x65, (byte) 0x2e,
            (byte) 0x63, (byte) 0x6f, (byte) 0x6d, (byte) 0x30, (byte) 0x81, (byte) 0x9f,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x8d,
            (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89, (byte) 0x02, (byte) 0x81,
            (byte) 0x81, (byte) 0x00, (byte) 0xce, (byte) 0x29, (byte) 0xeb, (byte) 0xf6,
            (byte) 0x5b, (byte) 0x25, (byte) 0xdc, (byte) 0xa1, (byte) 0xa6, (byte) 0x2c,
            (byte) 0x66, (byte) 0xcb, (byte) 0x20, (byte) 0x90, (byte) 0x27, (byte) 0x86,
            (byte) 0x8a, (byte) 0x44, (byte) 0x71, (byte) 0x50, (byte) 0xda, (byte) 0xd3,
            (byte) 0x02, (byte) 0x77, (byte) 0x55, (byte) 0xe9, (byte) 0xe8, (byte) 0x08,
            (byte) 0xf3, (byte) 0x36, (byte) 0x9a, (byte) 0xae, (byte) 0xab, (byte) 0x04,
            (byte) 0x6d, (byte) 0x00, (byte) 0x99, (byte) 0xbf, (byte) 0x7d, (byte) 0x0f,
            (byte) 0x67, (byte) 0x8b, (byte) 0x1d, (byte) 0xd4, (byte) 0x2b, (byte) 0x7c,
            (byte) 0xcb, (byte) 0xcd, (byte) 0x33, (byte) 0xc7, (byte) 0x84, (byte) 0x30,
            (byte) 0xe2, (byte) 0x45, (byte) 0x21, (byte) 0xb3, (byte) 0x75, (byte) 0xf5,
            (byte) 0x79, (byte) 0x02, (byte) 0xda, (byte) 0x50, (byte) 0xa3, (byte) 0x8b,
            (byte) 0xce, (byte) 0xc3, (byte) 0x8e, (byte) 0x0f, (byte) 0x25, (byte) 0xeb,
            (byte) 0x08, (byte) 0x2c, (byte) 0xdd, (byte) 0x1c, (byte) 0xcf, (byte) 0xff,
            (byte) 0x3b, (byte) 0xde, (byte) 0xb6, (byte) 0xaa, (byte) 0x2a, (byte) 0xa9,
            (byte) 0xc4, (byte) 0x8a, (byte) 0x24, (byte) 0x24, (byte) 0xe6, (byte) 0x29,
            (byte) 0x0d, (byte) 0x98, (byte) 0x4c, (byte) 0x32, (byte) 0xa1, (byte) 0x7b,
            (byte) 0x23, (byte) 0x2b, (byte) 0x42, (byte) 0x30, (byte) 0xee, (byte) 0x78,
            (byte) 0x08, (byte) 0x47, (byte) 0xad, (byte) 0xf2, (byte) 0x96, (byte) 0xd5,
            (byte) 0xf1, (byte) 0x62, (byte) 0x42, (byte) 0x2d, (byte) 0x35, (byte) 0x19,
            (byte) 0xb4, (byte) 0x3c, (byte) 0xc9, (byte) 0xc3, (byte) 0x5f, (byte) 0x03,
            (byte) 0x16, (byte) 0x3a, (byte) 0x23, (byte) 0xac, (byte) 0xcb, (byte) 0xce,
            (byte) 0x9e, (byte) 0x51, (byte) 0x2e, (byte) 0x6d, (byte) 0x02, (byte) 0x03,
            (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xa3, (byte) 0x7b, (byte) 0x30,
            (byte) 0x79, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x1d, (byte) 0x13, (byte) 0x04, (byte) 0x02, (byte) 0x30, (byte) 0x00,
            (byte) 0x30, (byte) 0x2c, (byte) 0x06, (byte) 0x09, (byte) 0x60, (byte) 0x86,
            (byte) 0x48, (byte) 0x01, (byte) 0x86, (byte) 0xf8, (byte) 0x42, (byte) 0x01,
            (byte) 0x0d, (byte) 0x04, (byte) 0x1f, (byte) 0x16, (byte) 0x1d, (byte) 0x4f,
            (byte) 0x70, (byte) 0x65, (byte) 0x6e, (byte) 0x53, (byte) 0x53, (byte) 0x4c,
            (byte) 0x20, (byte) 0x47, (byte) 0x65, (byte) 0x6e, (byte) 0x65, (byte) 0x72,
            (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x64, (byte) 0x20, (byte) 0x43,
            (byte) 0x65, (byte) 0x72, (byte) 0x74, (byte) 0x69, (byte) 0x66, (byte) 0x69,
            (byte) 0x63, (byte) 0x61, (byte) 0x74, (byte) 0x65, (byte) 0x30, (byte) 0x1d,
            (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x0e, (byte) 0x04,
            (byte) 0x16, (byte) 0x04, (byte) 0x14, (byte) 0x32, (byte) 0xa1, (byte) 0x1e,
            (byte) 0x6b, (byte) 0x69, (byte) 0x04, (byte) 0xfe, (byte) 0xb3, (byte) 0xcd,
            (byte) 0xf8, (byte) 0xbb, (byte) 0x14, (byte) 0xcd, (byte) 0xff, (byte) 0xd4,
            (byte) 0x16, (byte) 0xc3, (byte) 0xab, (byte) 0x44, (byte) 0x2f, (byte) 0x30,
            (byte) 0x1f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x23,
            (byte) 0x04, (byte) 0x18, (byte) 0x30, (byte) 0x16, (byte) 0x80, (byte) 0x14,
            (byte) 0x33, (byte) 0x05, (byte) 0xee, (byte) 0xfe, (byte) 0x6f, (byte) 0x60,
            (byte) 0xc7, (byte) 0xf9, (byte) 0xa9, (byte) 0xd2, (byte) 0x73, (byte) 0x5c,
            (byte) 0x8f, (byte) 0x6d, (byte) 0xa2, (byte) 0x2f, (byte) 0x97, (byte) 0x8e,
            (byte) 0x5d, (byte) 0x51, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09,
            (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x03,
            (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0x46, (byte) 0x42, (byte) 0xef,
            (byte) 0x56, (byte) 0x89, (byte) 0x78, (byte) 0x90, (byte) 0x38, (byte) 0x24,
            (byte) 0x9f, (byte) 0x8c, (byte) 0x7a, (byte) 0xce, (byte) 0x7a, (byte) 0xa5,
            (byte) 0xb5, (byte) 0x1e, (byte) 0x74, (byte) 0x96, (byte) 0x34, (byte) 0x49,
            (byte) 0x8b, (byte) 0xed, (byte) 0x44, (byte) 0xb3, (byte) 0xc9, (byte) 0x05,
            (byte) 0xd7, (byte) 0x48, (byte) 0x55, (byte) 0x52, (byte) 0x59, (byte) 0x15,
            (byte) 0x0b, (byte) 0xaa, (byte) 0x16, (byte) 0x86, (byte) 0xd2, (byte) 0x8e,
            (byte) 0x16, (byte) 0x99, (byte) 0xe8, (byte) 0x5f, (byte) 0x11, (byte) 0x71,
            (byte) 0x42, (byte) 0x55, (byte) 0xd1, (byte) 0xc4, (byte) 0x6f, (byte) 0x2e,
            (byte) 0xa9, (byte) 0x64, (byte) 0x6f, (byte) 0xd8, (byte) 0xfd, (byte) 0x43,
            (byte) 0x13, (byte) 0x24, (byte) 0xaa, (byte) 0x67, (byte) 0xe6, (byte) 0xf5,
            (byte) 0xca, (byte) 0x80, (byte) 0x5e, (byte) 0x3a, (byte) 0x3e, (byte) 0xcc,
            (byte) 0x4f, (byte) 0xba, (byte) 0x87, (byte) 0xe6, (byte) 0xae, (byte) 0xbf,
            (byte) 0x8f, (byte) 0xd5, (byte) 0x28, (byte) 0x38, (byte) 0x58, (byte) 0x30,
            (byte) 0x24, (byte) 0xf6, (byte) 0x53, (byte) 0x5b, (byte) 0x41, (byte) 0x53,
            (byte) 0xe6, (byte) 0x45, (byte) 0xbc, (byte) 0xbe, (byte) 0xe6, (byte) 0xbb,
            (byte) 0x5d, (byte) 0xd8, (byte) 0xa7, (byte) 0xf9, (byte) 0x64, (byte) 0x99,
            (byte) 0x04, (byte) 0x43, (byte) 0x75, (byte) 0xd7, (byte) 0x2d, (byte) 0x32,
            (byte) 0x0a, (byte) 0x94, (byte) 0xaf, (byte) 0x06, (byte) 0x34, (byte) 0xae,
            (byte) 0x46, (byte) 0xbd, (byte) 0xda, (byte) 0x00, (byte) 0x0e, (byte) 0x25,
            (byte) 0xc2, (byte) 0xf7, (byte) 0xc9, (byte) 0xc3, (byte) 0x65, (byte) 0xd2,
            (byte) 0x08, (byte) 0x41, (byte) 0x0a, (byte) 0xf3, (byte) 0x72
    };

    /**
     * Generated from above and converted with:
     *
     * openssl x509 -outform d -in cacert.pem | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_CA_1 = {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0xce, (byte) 0x30, (byte) 0x82,
            (byte) 0x02, (byte) 0x37, (byte) 0xa0, (byte) 0x03, (byte) 0x02, (byte) 0x01,
            (byte) 0x02, (byte) 0x02, (byte) 0x09, (byte) 0x00, (byte) 0xe1, (byte) 0x6a,
            (byte) 0xa2, (byte) 0xf4, (byte) 0x2e, (byte) 0x55, (byte) 0x48, (byte) 0x0a,
            (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86,
            (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01,
            (byte) 0x05, (byte) 0x05, (byte) 0x00, (byte) 0x30, (byte) 0x4f, (byte) 0x31,
            (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53,
            (byte) 0x31, (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43,
            (byte) 0x41, (byte) 0x31, (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d,
            (byte) 0x4d, (byte) 0x6f, (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61,
            (byte) 0x69, (byte) 0x6e, (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65,
            (byte) 0x77, (byte) 0x31, (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12,
            (byte) 0x41, (byte) 0x6e, (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69,
            (byte) 0x64, (byte) 0x20, (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74,
            (byte) 0x20, (byte) 0x43, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73,
            (byte) 0x30, (byte) 0x1e, (byte) 0x17, (byte) 0x0d, (byte) 0x31, (byte) 0x32,
            (byte) 0x30, (byte) 0x38, (byte) 0x31, (byte) 0x34, (byte) 0x31, (byte) 0x36,
            (byte) 0x35, (byte) 0x35, (byte) 0x34, (byte) 0x34, (byte) 0x5a, (byte) 0x17,
            (byte) 0x0d, (byte) 0x32, (byte) 0x32, (byte) 0x30, (byte) 0x38, (byte) 0x31,
            (byte) 0x32, (byte) 0x31, (byte) 0x36, (byte) 0x35, (byte) 0x35, (byte) 0x34,
            (byte) 0x34, (byte) 0x5a, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x06, (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31,
            (byte) 0x0b, (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41,
            (byte) 0x31, (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d,
            (byte) 0x6f, (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69,
            (byte) 0x6e, (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77,
            (byte) 0x31, (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03,
            (byte) 0x55, (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41,
            (byte) 0x6e, (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64,
            (byte) 0x20, (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20,
            (byte) 0x43, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x30,
            (byte) 0x81, (byte) 0x9f, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09,
            (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,
            (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03,
            (byte) 0x81, (byte) 0x8d, (byte) 0x00, (byte) 0x30, (byte) 0x81, (byte) 0x89,
            (byte) 0x02, (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0xa3, (byte) 0x72,
            (byte) 0xab, (byte) 0xd0, (byte) 0xe4, (byte) 0xad, (byte) 0x2f, (byte) 0xe7,
            (byte) 0xe2, (byte) 0x79, (byte) 0x07, (byte) 0x36, (byte) 0x3d, (byte) 0x0c,
            (byte) 0x8d, (byte) 0x42, (byte) 0x9a, (byte) 0x0a, (byte) 0x33, (byte) 0x64,
            (byte) 0xb3, (byte) 0xcd, (byte) 0xb2, (byte) 0xd7, (byte) 0x3a, (byte) 0x42,
            (byte) 0x06, (byte) 0x77, (byte) 0x45, (byte) 0x29, (byte) 0xe9, (byte) 0xcb,
            (byte) 0xb7, (byte) 0x4a, (byte) 0xd6, (byte) 0xee, (byte) 0xad, (byte) 0x01,
            (byte) 0x91, (byte) 0x9b, (byte) 0x0c, (byte) 0x59, (byte) 0xa1, (byte) 0x03,
            (byte) 0xfa, (byte) 0xf0, (byte) 0x5a, (byte) 0x7c, (byte) 0x4f, (byte) 0xf7,
            (byte) 0x8d, (byte) 0x36, (byte) 0x0f, (byte) 0x1f, (byte) 0x45, (byte) 0x7d,
            (byte) 0x1b, (byte) 0x31, (byte) 0xa1, (byte) 0x35, (byte) 0x0b, (byte) 0x00,
            (byte) 0xed, (byte) 0x7a, (byte) 0xb6, (byte) 0xc8, (byte) 0x4e, (byte) 0xa9,
            (byte) 0x86, (byte) 0x4c, (byte) 0x7b, (byte) 0x99, (byte) 0x57, (byte) 0x41,
            (byte) 0x12, (byte) 0xef, (byte) 0x6b, (byte) 0xbc, (byte) 0x3d, (byte) 0x60,
            (byte) 0xf2, (byte) 0x99, (byte) 0x1a, (byte) 0xcd, (byte) 0xed, (byte) 0x56,
            (byte) 0xa4, (byte) 0xe5, (byte) 0x36, (byte) 0x9f, (byte) 0x24, (byte) 0x1f,
            (byte) 0xdc, (byte) 0x89, (byte) 0x40, (byte) 0xc8, (byte) 0x99, (byte) 0x92,
            (byte) 0xab, (byte) 0x4a, (byte) 0xb5, (byte) 0x61, (byte) 0x45, (byte) 0x62,
            (byte) 0xff, (byte) 0xa3, (byte) 0x45, (byte) 0x65, (byte) 0xaf, (byte) 0xf6,
            (byte) 0x27, (byte) 0x30, (byte) 0x51, (byte) 0x0e, (byte) 0x0e, (byte) 0xeb,
            (byte) 0x79, (byte) 0x0c, (byte) 0xbe, (byte) 0xb3, (byte) 0x0a, (byte) 0x6f,
            (byte) 0x29, (byte) 0x06, (byte) 0xdc, (byte) 0x2f, (byte) 0x6b, (byte) 0x51,
            (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xa3,
            (byte) 0x81, (byte) 0xb1, (byte) 0x30, (byte) 0x81, (byte) 0xae, (byte) 0x30,
            (byte) 0x1d, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x0e,
            (byte) 0x04, (byte) 0x16, (byte) 0x04, (byte) 0x14, (byte) 0x33, (byte) 0x05,
            (byte) 0xee, (byte) 0xfe, (byte) 0x6f, (byte) 0x60, (byte) 0xc7, (byte) 0xf9,
            (byte) 0xa9, (byte) 0xd2, (byte) 0x73, (byte) 0x5c, (byte) 0x8f, (byte) 0x6d,
            (byte) 0xa2, (byte) 0x2f, (byte) 0x97, (byte) 0x8e, (byte) 0x5d, (byte) 0x51,
            (byte) 0x30, (byte) 0x7f, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x1d,
            (byte) 0x23, (byte) 0x04, (byte) 0x78, (byte) 0x30, (byte) 0x76, (byte) 0x80,
            (byte) 0x14, (byte) 0x33, (byte) 0x05, (byte) 0xee, (byte) 0xfe, (byte) 0x6f,
            (byte) 0x60, (byte) 0xc7, (byte) 0xf9, (byte) 0xa9, (byte) 0xd2, (byte) 0x73,
            (byte) 0x5c, (byte) 0x8f, (byte) 0x6d, (byte) 0xa2, (byte) 0x2f, (byte) 0x97,
            (byte) 0x8e, (byte) 0x5d, (byte) 0x51, (byte) 0xa1, (byte) 0x53, (byte) 0xa4,
            (byte) 0x51, (byte) 0x30, (byte) 0x4f, (byte) 0x31, (byte) 0x0b, (byte) 0x30,
            (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04, (byte) 0x06,
            (byte) 0x13, (byte) 0x02, (byte) 0x55, (byte) 0x53, (byte) 0x31, (byte) 0x0b,
            (byte) 0x30, (byte) 0x09, (byte) 0x06, (byte) 0x03, (byte) 0x55, (byte) 0x04,
            (byte) 0x08, (byte) 0x13, (byte) 0x02, (byte) 0x43, (byte) 0x41, (byte) 0x31,
            (byte) 0x16, (byte) 0x30, (byte) 0x14, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x07, (byte) 0x13, (byte) 0x0d, (byte) 0x4d, (byte) 0x6f,
            (byte) 0x75, (byte) 0x6e, (byte) 0x74, (byte) 0x61, (byte) 0x69, (byte) 0x6e,
            (byte) 0x20, (byte) 0x56, (byte) 0x69, (byte) 0x65, (byte) 0x77, (byte) 0x31,
            (byte) 0x1b, (byte) 0x30, (byte) 0x19, (byte) 0x06, (byte) 0x03, (byte) 0x55,
            (byte) 0x04, (byte) 0x0a, (byte) 0x13, (byte) 0x12, (byte) 0x41, (byte) 0x6e,
            (byte) 0x64, (byte) 0x72, (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x20,
            (byte) 0x54, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x20, (byte) 0x43,
            (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x73, (byte) 0x82, (byte) 0x09,
            (byte) 0x00, (byte) 0xe1, (byte) 0x6a, (byte) 0xa2, (byte) 0xf4, (byte) 0x2e,
            (byte) 0x55, (byte) 0x48, (byte) 0x0a, (byte) 0x30, (byte) 0x0c, (byte) 0x06,
            (byte) 0x03, (byte) 0x55, (byte) 0x1d, (byte) 0x13, (byte) 0x04, (byte) 0x05,
            (byte) 0x30, (byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0xff, (byte) 0x30,
            (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a, (byte) 0x86, (byte) 0x48,
            (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x05,
            (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x81, (byte) 0x81, (byte) 0x00,
            (byte) 0x8c, (byte) 0x30, (byte) 0x42, (byte) 0xfa, (byte) 0xeb, (byte) 0x1a,
            (byte) 0x26, (byte) 0xeb, (byte) 0xda, (byte) 0x56, (byte) 0x32, (byte) 0xf2,
            (byte) 0x9d, (byte) 0xa5, (byte) 0x24, (byte) 0xd8, (byte) 0x3a, (byte) 0xda,
            (byte) 0x30, (byte) 0xa6, (byte) 0x8b, (byte) 0x46, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xdb, (byte) 0xf1, (byte) 0xe6, (byte) 0xe1, (byte) 0x7c, (byte) 0x1b,
            (byte) 0xe7, (byte) 0x77, (byte) 0x00, (byte) 0xa1, (byte) 0x1c, (byte) 0x19,
            (byte) 0x17, (byte) 0x73, (byte) 0xb0, (byte) 0xf0, (byte) 0x9d, (byte) 0xf3,
            (byte) 0x4f, (byte) 0xb6, (byte) 0xbc, (byte) 0xc7, (byte) 0x47, (byte) 0x85,
            (byte) 0x2a, (byte) 0x4a, (byte) 0xa1, (byte) 0xa5, (byte) 0x58, (byte) 0xf5,
            (byte) 0xc5, (byte) 0x1a, (byte) 0x51, (byte) 0xb1, (byte) 0x04, (byte) 0x80,
            (byte) 0xee, (byte) 0x3a, (byte) 0xec, (byte) 0x2f, (byte) 0xe1, (byte) 0xfd,
            (byte) 0x58, (byte) 0xeb, (byte) 0xed, (byte) 0x82, (byte) 0x9e, (byte) 0x38,
            (byte) 0xa3, (byte) 0x24, (byte) 0x75, (byte) 0xf7, (byte) 0x3e, (byte) 0xc2,
            (byte) 0xc5, (byte) 0x27, (byte) 0xeb, (byte) 0x6f, (byte) 0x7b, (byte) 0x50,
            (byte) 0xda, (byte) 0x43, (byte) 0xdc, (byte) 0x3b, (byte) 0x0b, (byte) 0x6f,
            (byte) 0x78, (byte) 0x8f, (byte) 0xb0, (byte) 0x66, (byte) 0xe1, (byte) 0x12,
            (byte) 0x87, (byte) 0x5f, (byte) 0x97, (byte) 0x7b, (byte) 0xca, (byte) 0x14,
            (byte) 0x79, (byte) 0xf7, (byte) 0xe8, (byte) 0x6c, (byte) 0x72, (byte) 0xdb,
            (byte) 0x91, (byte) 0x65, (byte) 0x17, (byte) 0x54, (byte) 0xe0, (byte) 0x74,
            (byte) 0x1d, (byte) 0xac, (byte) 0x47, (byte) 0x04, (byte) 0x12, (byte) 0xe0,
            (byte) 0xc3, (byte) 0x66, (byte) 0x19, (byte) 0x05, (byte) 0x2e, (byte) 0x7e,
            (byte) 0xf1, (byte) 0x61
    };

    /**
     * Generated from above and converted with:
     *
     * openssl pkcs8 -topk8 -outform d -in userkey.pem -nocrypt | xxd -i | sed 's/0x/(byte) 0x/g'
     */
    private static final byte[] FAKE_RSA_KEY_1 = new byte[] {
            (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x78, (byte) 0x02, (byte) 0x01,
            (byte) 0x00, (byte) 0x30, (byte) 0x0d, (byte) 0x06, (byte) 0x09, (byte) 0x2a,
            (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x04, (byte) 0x82,
            (byte) 0x02, (byte) 0x62, (byte) 0x30, (byte) 0x82, (byte) 0x02, (byte) 0x5e,
            (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x81, (byte) 0x81,
            (byte) 0x00, (byte) 0xce, (byte) 0x29, (byte) 0xeb, (byte) 0xf6, (byte) 0x5b,
            (byte) 0x25, (byte) 0xdc, (byte) 0xa1, (byte) 0xa6, (byte) 0x2c, (byte) 0x66,
            (byte) 0xcb, (byte) 0x20, (byte) 0x90, (byte) 0x27, (byte) 0x86, (byte) 0x8a,
            (byte) 0x44, (byte) 0x71, (byte) 0x50, (byte) 0xda, (byte) 0xd3, (byte) 0x02,
            (byte) 0x77, (byte) 0x55, (byte) 0xe9, (byte) 0xe8, (byte) 0x08, (byte) 0xf3,
            (byte) 0x36, (byte) 0x9a, (byte) 0xae, (byte) 0xab, (byte) 0x04, (byte) 0x6d,
            (byte) 0x00, (byte) 0x99, (byte) 0xbf, (byte) 0x7d, (byte) 0x0f, (byte) 0x67,
            (byte) 0x8b, (byte) 0x1d, (byte) 0xd4, (byte) 0x2b, (byte) 0x7c, (byte) 0xcb,
            (byte) 0xcd, (byte) 0x33, (byte) 0xc7, (byte) 0x84, (byte) 0x30, (byte) 0xe2,
            (byte) 0x45, (byte) 0x21, (byte) 0xb3, (byte) 0x75, (byte) 0xf5, (byte) 0x79,
            (byte) 0x02, (byte) 0xda, (byte) 0x50, (byte) 0xa3, (byte) 0x8b, (byte) 0xce,
            (byte) 0xc3, (byte) 0x8e, (byte) 0x0f, (byte) 0x25, (byte) 0xeb, (byte) 0x08,
            (byte) 0x2c, (byte) 0xdd, (byte) 0x1c, (byte) 0xcf, (byte) 0xff, (byte) 0x3b,
            (byte) 0xde, (byte) 0xb6, (byte) 0xaa, (byte) 0x2a, (byte) 0xa9, (byte) 0xc4,
            (byte) 0x8a, (byte) 0x24, (byte) 0x24, (byte) 0xe6, (byte) 0x29, (byte) 0x0d,
            (byte) 0x98, (byte) 0x4c, (byte) 0x32, (byte) 0xa1, (byte) 0x7b, (byte) 0x23,
            (byte) 0x2b, (byte) 0x42, (byte) 0x30, (byte) 0xee, (byte) 0x78, (byte) 0x08,
            (byte) 0x47, (byte) 0xad, (byte) 0xf2, (byte) 0x96, (byte) 0xd5, (byte) 0xf1,
            (byte) 0x62, (byte) 0x42, (byte) 0x2d, (byte) 0x35, (byte) 0x19, (byte) 0xb4,
            (byte) 0x3c, (byte) 0xc9, (byte) 0xc3, (byte) 0x5f, (byte) 0x03, (byte) 0x16,
            (byte) 0x3a, (byte) 0x23, (byte) 0xac, (byte) 0xcb, (byte) 0xce, (byte) 0x9e,
            (byte) 0x51, (byte) 0x2e, (byte) 0x6d, (byte) 0x02, (byte) 0x03, (byte) 0x01,
            (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x81, (byte) 0x80, (byte) 0x16,
            (byte) 0x59, (byte) 0xc3, (byte) 0x24, (byte) 0x1d, (byte) 0x33, (byte) 0x98,
            (byte) 0x9c, (byte) 0xc9, (byte) 0xc8, (byte) 0x2c, (byte) 0x88, (byte) 0xbf,
            (byte) 0x0a, (byte) 0x01, (byte) 0xce, (byte) 0xfb, (byte) 0x34, (byte) 0x7a,
            (byte) 0x58, (byte) 0x7a, (byte) 0xb0, (byte) 0xbf, (byte) 0xa6, (byte) 0xb2,
            (byte) 0x60, (byte) 0xbe, (byte) 0x70, (byte) 0x21, (byte) 0xf5, (byte) 0xfc,
            (byte) 0x85, (byte) 0x0d, (byte) 0x33, (byte) 0x58, (byte) 0xa1, (byte) 0xe5,
            (byte) 0x09, (byte) 0x36, (byte) 0x84, (byte) 0xb2, (byte) 0x04, (byte) 0x0a,
            (byte) 0x02, (byte) 0xd3, (byte) 0x88, (byte) 0x1f, (byte) 0x0c, (byte) 0x2b,
            (byte) 0x1d, (byte) 0xe9, (byte) 0x3d, (byte) 0xe7, (byte) 0x79, (byte) 0xf9,
            (byte) 0x32, (byte) 0x5c, (byte) 0x8a, (byte) 0x75, (byte) 0x49, (byte) 0x12,
            (byte) 0xe4, (byte) 0x05, (byte) 0x26, (byte) 0xd4, (byte) 0x2e, (byte) 0x9e,
            (byte) 0x1f, (byte) 0xcc, (byte) 0x54, (byte) 0xad, (byte) 0x33, (byte) 0x8d,
            (byte) 0x99, (byte) 0x00, (byte) 0xdc, (byte) 0xf5, (byte) 0xb4, (byte) 0xa2,
            (byte) 0x2f, (byte) 0xba, (byte) 0xe5, (byte) 0x62, (byte) 0x30, (byte) 0x6d,
            (byte) 0xe6, (byte) 0x3d, (byte) 0xeb, (byte) 0x24, (byte) 0xc2, (byte) 0xdc,
            (byte) 0x5f, (byte) 0xb7, (byte) 0x16, (byte) 0x35, (byte) 0xa3, (byte) 0x98,
            (byte) 0x98, (byte) 0xa8, (byte) 0xef, (byte) 0xe8, (byte) 0xc4, (byte) 0x96,
            (byte) 0x6d, (byte) 0x38, (byte) 0xab, (byte) 0x26, (byte) 0x6d, (byte) 0x30,
            (byte) 0xc2, (byte) 0xa0, (byte) 0x44, (byte) 0xe4, (byte) 0xff, (byte) 0x7e,
            (byte) 0xbe, (byte) 0x7c, (byte) 0x33, (byte) 0xa5, (byte) 0x10, (byte) 0xad,
            (byte) 0xd7, (byte) 0x1e, (byte) 0x13, (byte) 0x20, (byte) 0xb3, (byte) 0x1f,
            (byte) 0x41, (byte) 0x02, (byte) 0x41, (byte) 0x00, (byte) 0xf1, (byte) 0x89,
            (byte) 0x07, (byte) 0x0f, (byte) 0xe8, (byte) 0xcf, (byte) 0xab, (byte) 0x13,
            (byte) 0x2a, (byte) 0x8f, (byte) 0x88, (byte) 0x80, (byte) 0x11, (byte) 0x9a,
            (byte) 0x79, (byte) 0xb6, (byte) 0x59, (byte) 0x3a, (byte) 0x50, (byte) 0x6e,
            (byte) 0x57, (byte) 0x37, (byte) 0xab, (byte) 0x2a, (byte) 0xd2, (byte) 0xaa,
            (byte) 0xd9, (byte) 0x72, (byte) 0x73, (byte) 0xff, (byte) 0x8b, (byte) 0x47,
            (byte) 0x76, (byte) 0xdd, (byte) 0xdc, (byte) 0xf5, (byte) 0x97, (byte) 0x44,
            (byte) 0x3a, (byte) 0x78, (byte) 0xbe, (byte) 0x17, (byte) 0xb4, (byte) 0x22,
            (byte) 0x6f, (byte) 0xe5, (byte) 0x23, (byte) 0x70, (byte) 0x1d, (byte) 0x10,
            (byte) 0x5d, (byte) 0xba, (byte) 0x16, (byte) 0x81, (byte) 0xf1, (byte) 0x45,
            (byte) 0xce, (byte) 0x30, (byte) 0xb4, (byte) 0xab, (byte) 0x80, (byte) 0xe4,
            (byte) 0x98, (byte) 0x31, (byte) 0x02, (byte) 0x41, (byte) 0x00, (byte) 0xda,
            (byte) 0x82, (byte) 0x9d, (byte) 0x3f, (byte) 0xca, (byte) 0x2f, (byte) 0xe1,
            (byte) 0xd4, (byte) 0x86, (byte) 0x77, (byte) 0x48, (byte) 0xa6, (byte) 0xab,
            (byte) 0xab, (byte) 0x1c, (byte) 0x42, (byte) 0x5c, (byte) 0xd5, (byte) 0xc7,
            (byte) 0x46, (byte) 0x59, (byte) 0x91, (byte) 0x3f, (byte) 0xfc, (byte) 0xcc,
            (byte) 0xec, (byte) 0xc2, (byte) 0x40, (byte) 0x12, (byte) 0x2c, (byte) 0x8d,
            (byte) 0x1f, (byte) 0xa2, (byte) 0x18, (byte) 0x88, (byte) 0xee, (byte) 0x82,
            (byte) 0x4a, (byte) 0x5a, (byte) 0x5e, (byte) 0x88, (byte) 0x20, (byte) 0xe3,
            (byte) 0x7b, (byte) 0xe0, (byte) 0xd8, (byte) 0x3a, (byte) 0x52, (byte) 0x9a,
            (byte) 0x26, (byte) 0x6a, (byte) 0x04, (byte) 0xec, (byte) 0xe8, (byte) 0xb9,
            (byte) 0x48, (byte) 0x40, (byte) 0xe1, (byte) 0xe1, (byte) 0x83, (byte) 0xa6,
            (byte) 0x67, (byte) 0xa6, (byte) 0xfd, (byte) 0x02, (byte) 0x41, (byte) 0x00,
            (byte) 0x89, (byte) 0x72, (byte) 0x3e, (byte) 0xb0, (byte) 0x90, (byte) 0xfd,
            (byte) 0x4c, (byte) 0x0e, (byte) 0xd6, (byte) 0x13, (byte) 0x63, (byte) 0xcb,
            (byte) 0xed, (byte) 0x38, (byte) 0x88, (byte) 0xb6, (byte) 0x79, (byte) 0xc4,
            (byte) 0x33, (byte) 0x6c, (byte) 0xf6, (byte) 0xf8, (byte) 0xd8, (byte) 0xd0,
            (byte) 0xbf, (byte) 0x9d, (byte) 0x35, (byte) 0xac, (byte) 0x69, (byte) 0xd2,
            (byte) 0x2b, (byte) 0xc1, (byte) 0xf9, (byte) 0x24, (byte) 0x7b, (byte) 0xce,
            (byte) 0xcd, (byte) 0xcb, (byte) 0xa7, (byte) 0xb2, (byte) 0x7a, (byte) 0x0a,
            (byte) 0x27, (byte) 0x19, (byte) 0xc9, (byte) 0xaf, (byte) 0x0d, (byte) 0x21,
            (byte) 0x89, (byte) 0x88, (byte) 0x7c, (byte) 0xad, (byte) 0x9e, (byte) 0x8d,
            (byte) 0x47, (byte) 0x6d, (byte) 0x3f, (byte) 0xce, (byte) 0x7b, (byte) 0xa1,
            (byte) 0x74, (byte) 0xf1, (byte) 0xa0, (byte) 0xa1, (byte) 0x02, (byte) 0x41,
            (byte) 0x00, (byte) 0xd9, (byte) 0xa8, (byte) 0xf5, (byte) 0xfe, (byte) 0xce,
            (byte) 0xe6, (byte) 0x77, (byte) 0x6b, (byte) 0xfe, (byte) 0x2d, (byte) 0xe0,
            (byte) 0x1e, (byte) 0xb6, (byte) 0x2e, (byte) 0x12, (byte) 0x4e, (byte) 0x40,
            (byte) 0xaf, (byte) 0x6a, (byte) 0x7b, (byte) 0x37, (byte) 0x49, (byte) 0x2a,
            (byte) 0x96, (byte) 0x25, (byte) 0x83, (byte) 0x49, (byte) 0xd4, (byte) 0x0c,
            (byte) 0xc6, (byte) 0x78, (byte) 0x25, (byte) 0x24, (byte) 0x90, (byte) 0x90,
            (byte) 0x06, (byte) 0x15, (byte) 0x9e, (byte) 0xfe, (byte) 0xf9, (byte) 0xdf,
            (byte) 0x5b, (byte) 0xf3, (byte) 0x7e, (byte) 0x38, (byte) 0x70, (byte) 0xeb,
            (byte) 0x57, (byte) 0xd0, (byte) 0xd9, (byte) 0xa7, (byte) 0x0e, (byte) 0x14,
            (byte) 0xf7, (byte) 0x95, (byte) 0x68, (byte) 0xd5, (byte) 0xc8, (byte) 0xab,
            (byte) 0x9d, (byte) 0x3a, (byte) 0x2b, (byte) 0x51, (byte) 0xf9, (byte) 0x02,
            (byte) 0x41, (byte) 0x00, (byte) 0x96, (byte) 0xdf, (byte) 0xe9, (byte) 0x67,
            (byte) 0x6c, (byte) 0xdc, (byte) 0x90, (byte) 0x14, (byte) 0xb4, (byte) 0x1d,
            (byte) 0x22, (byte) 0x33, (byte) 0x4a, (byte) 0x31, (byte) 0xc1, (byte) 0x9d,
            (byte) 0x2e, (byte) 0xff, (byte) 0x9a, (byte) 0x2a, (byte) 0x95, (byte) 0x4b,
            (byte) 0x27, (byte) 0x74, (byte) 0xcb, (byte) 0x21, (byte) 0xc3, (byte) 0xd2,
            (byte) 0x0b, (byte) 0xb2, (byte) 0x46, (byte) 0x87, (byte) 0xf8, (byte) 0x28,
            (byte) 0x01, (byte) 0x8b, (byte) 0xd8, (byte) 0xb9, (byte) 0x4b, (byte) 0xcd,
            (byte) 0x9a, (byte) 0x96, (byte) 0x41, (byte) 0x0e, (byte) 0x36, (byte) 0x6d,
            (byte) 0x40, (byte) 0x42, (byte) 0xbc, (byte) 0xd9, (byte) 0xd3, (byte) 0x7b,
            (byte) 0xbc, (byte) 0xa7, (byte) 0x92, (byte) 0x90, (byte) 0xdd, (byte) 0xa1,
            (byte) 0x9c, (byte) 0xce, (byte) 0xa1, (byte) 0x87, (byte) 0x11, (byte) 0x51
    };


    public WebViewTest() {
        super("com.android.cts.stub", WebViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final WebViewStubActivity activity = getActivity();
        mWebView = activity.getWebView();
        if (mWebView != null) {
            new PollingCheck() {
                @Override
                    protected boolean check() {
                        return activity.hasWindowFocus();
                }
            }.run();
            File f = activity.getFileStreamPath("snapshot");
            if (f.exists()) {
                f.delete();
            }

            mOnUiThread = new WebViewOnUiThread(this, mWebView);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        if (mWebServer != null) {
            stopWebServer();
        }
        if (mIconDb != null) {
            mIconDb.removeAllIcons();
            mIconDb.close();
            mIconDb = null;
        }
        super.tearDown();
    }

    private void startWebServer(boolean secure) throws Exception {
        assertNull(mWebServer);
        mWebServer = new CtsTestServer(getActivity(), secure);
    }

    private void stopWebServer() throws Exception {
        assertNotNull(mWebServer);
        ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        ThreadPolicy tmpPolicy = new ThreadPolicy.Builder(oldPolicy)
                .permitNetwork()
                .build();
        StrictMode.setThreadPolicy(tmpPolicy);
        mWebServer.shutdown();
        mWebServer = null;
        StrictMode.setThreadPolicy(oldPolicy);
    }

    @UiThreadTest
    public void testConstructor() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        new WebView(getActivity());
        new WebView(getActivity(), null);
        new WebView(getActivity(), null, 0);
    }

    @UiThreadTest
    public void testCreatingWebViewCreatesCookieSyncManager() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        new WebView(getActivity());
        assertNotNull(CookieSyncManager.getInstance());
    }

    @UiThreadTest
    public void testFindAddress() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        /*
         * Info about USPS
         * http://en.wikipedia.org/wiki/Postal_address#United_States
         * http://www.usps.com/
         */
        // full address
        assertEquals("455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA 92826",
                WebView.findAddress("455 LARKSPUR DRIVE CALIFORNIA SPRINGS CALIFORNIA 92826"));
        // not an address
        assertNull(WebView.findAddress("This is not an address: no town, no state, no zip."));
    }

    @SuppressWarnings("deprecation")
    @UiThreadTest
    public void testGetZoomControls() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        WebSettings settings = mWebView.getSettings();
        assertTrue(settings.supportZoom());
        View zoomControls = mWebView.getZoomControls();
        assertNotNull(zoomControls);

        // disable zoom support
        settings.setSupportZoom(false);
        assertFalse(settings.supportZoom());
        assertNull(mWebView.getZoomControls());
    }

    @UiThreadTest
    public void testInvokeZoomPicker() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        WebSettings settings = mWebView.getSettings();
        assertTrue(settings.supportZoom());
        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        mWebView.invokeZoomPicker();
    }

    public void testZoom() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        // Pinch zoom is not supported in wrap_content layouts.
        mOnUiThread.setLayoutHeightToMatchParent();

        final ScaleChangedWebViewClient webViewClient = new ScaleChangedWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);

        mWebServer = new CtsTestServer(getActivity());
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        pollingCheckForCanZoomIn();

        WebSettings settings = mOnUiThread.getSettings();
        settings.setSupportZoom(false);
        assertFalse(settings.supportZoom());
        float currScale = mOnUiThread.getScale();
        float previousScale = currScale;

        // can zoom in or out although zoom support is disabled in web settings
        assertTrue(mOnUiThread.zoomIn());
        webViewClient.waitForScaleChanged();

        currScale = mOnUiThread.getScale();
        assertTrue(currScale > previousScale);

        assertTrue(mOnUiThread.zoomOut());
        previousScale = currScale;
        webViewClient.waitForScaleChanged();

        currScale = mOnUiThread.getScale();
        assertTrue(currScale < previousScale);

        // enable zoom support
        settings.setSupportZoom(true);
        assertTrue(settings.supportZoom());
        previousScale = mOnUiThread.getScale();

        assertTrue(mOnUiThread.zoomIn());
        webViewClient.waitForScaleChanged();

        currScale = mOnUiThread.getScale();
        assertTrue(currScale > previousScale);

        // zoom in until it reaches maximum scale
        while (mOnUiThread.zoomIn()) {
            previousScale = currScale;
            webViewClient.waitForScaleChanged();
            currScale = mOnUiThread.getScale();
            assertTrue(currScale > previousScale);
        }

        previousScale = currScale;
        // can not zoom in further
        assertFalse(mOnUiThread.zoomIn());
        // We sleep to assert to the best of our ability
        // that a scale change does *not* happen.
        Thread.sleep(500);
        currScale = mOnUiThread.getScale();
        assertEquals(currScale, previousScale);

        // zoom out
        assertTrue(mOnUiThread.zoomOut());
        previousScale = currScale;
        webViewClient.waitForScaleChanged();
        currScale = mOnUiThread.getScale();
        assertTrue(currScale < previousScale);

        // zoom out until it reaches minimum scale
        while (mOnUiThread.zoomOut()) {
            previousScale = currScale;
            webViewClient.waitForScaleChanged();
            currScale = mOnUiThread.getScale();
            assertTrue(currScale < previousScale);
        }

        previousScale = currScale;
        assertFalse(mOnUiThread.zoomOut());

        // We sleep to assert to the best of our ability
        // that a scale change does *not* happen.
        Thread.sleep(500);
        currScale = mOnUiThread.getScale();
        assertEquals(currScale, previousScale);
    }

    @UiThreadTest
    public void testSetScrollBarStyle() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        assertFalse(mWebView.overlayHorizontalScrollbar());
        assertFalse(mWebView.overlayVerticalScrollbar());

        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        assertTrue(mWebView.overlayHorizontalScrollbar());
        assertTrue(mWebView.overlayVerticalScrollbar());

        mWebView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        assertFalse(mWebView.overlayHorizontalScrollbar());
        assertFalse(mWebView.overlayVerticalScrollbar());

        mWebView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        assertTrue(mWebView.overlayHorizontalScrollbar());
        assertTrue(mWebView.overlayVerticalScrollbar());
    }

    @UiThreadTest
    public void testScrollBarOverlay() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        mWebView.setHorizontalScrollbarOverlay(true);
        mWebView.setVerticalScrollbarOverlay(false);
        assertTrue(mWebView.overlayHorizontalScrollbar());
        assertFalse(mWebView.overlayVerticalScrollbar());

        mWebView.setHorizontalScrollbarOverlay(false);
        mWebView.setVerticalScrollbarOverlay(true);
        assertFalse(mWebView.overlayHorizontalScrollbar());
        assertTrue(mWebView.overlayVerticalScrollbar());
    }

    @UiThreadTest
    public void testLoadUrl() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        assertNull(mWebView.getUrl());
        assertNull(mWebView.getOriginalUrl());
        assertEquals(INITIAL_PROGRESS, mWebView.getProgress());

        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(100, mWebView.getProgress());
        assertEquals(url, mWebView.getUrl());
        assertEquals(url, mWebView.getOriginalUrl());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mWebView.getTitle());

        // verify that the request also includes X-Requested-With header
        HttpRequest request = mWebServer.getLastRequest(TestHtmlConstants.HELLO_WORLD_URL);
        Header[] matchingHeaders = request.getHeaders(X_REQUESTED_WITH);
        assertEquals(1, matchingHeaders.length);

        Header header = matchingHeaders[0];
        assertEquals(mWebView.getContext().getApplicationInfo().packageName, header.getValue());
    }

    @UiThreadTest
    public void testPostUrlWithNonNetworkUrl() throws Exception {
        final String nonNetworkUrl = "file:///android_asset/" + TestHtmlConstants.HELLO_WORLD_URL;

        mOnUiThread.postUrlAndWaitForCompletion(nonNetworkUrl, new byte[1]);

        // Test if the nonNetworkUrl is loaded
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mWebView.getTitle());
    }

    @UiThreadTest
    public void testPostUrlWithNetworkUrl() throws Exception {
        startWebServer(false);
        final String networkUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final String postDataString = "username=my_username&password=my_password";
        final byte[] postData = EncodingUtils.getBytes(postDataString, "BASE64");

        mOnUiThread.postUrlAndWaitForCompletion(networkUrl, postData);

        HttpRequest request = mWebServer.getLastRequest(TestHtmlConstants.HELLO_WORLD_URL);
        // The last request should be POST
        assertEquals(request.getRequestLine().getMethod(), "POST");

        // The last request should have a request body
        assertTrue(request instanceof HttpEntityEnclosingRequest);
        HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
        String entityString = EntityUtils.toString(entity);
        assertEquals(entityString, postDataString);
    }

    @UiThreadTest
    public void testLoadUrlDoesNotStripParamsWhenLoadingContentUrls() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        Uri.Builder uriBuilder = new Uri.Builder().scheme(
                ContentResolver.SCHEME_CONTENT).authority(MockContentProvider.AUTHORITY);
        uriBuilder.appendPath("foo.html").appendQueryParameter("param","bar");
        String url = uriBuilder.build().toString();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // verify the parameter is not stripped.
        Uri uri = Uri.parse(mWebView.getTitle());
        assertEquals("bar", uri.getQueryParameter("param"));
    }

    @UiThreadTest
    public void testAppInjectedXRequestedWithHeaderIsNotOverwritten() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        HashMap<String, String> map = new HashMap<String, String>();
        final String requester = "foo";
        map.put(X_REQUESTED_WITH, requester);
        mOnUiThread.loadUrlAndWaitForCompletion(url, map);

        // verify that the request also includes X-Requested-With header
        // but is not overwritten by the webview
        HttpRequest request = mWebServer.getLastRequest(TestHtmlConstants.HELLO_WORLD_URL);
        Header[] matchingHeaders = request.getHeaders(X_REQUESTED_WITH);
        assertEquals(1, matchingHeaders.length);

        Header header = matchingHeaders[0];
        assertEquals(requester, header.getValue());
    }

    @UiThreadTest
    public void testAppCanInjectHeadersViaImmutableMap() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        HashMap<String, String> map = new HashMap<String, String>();
        final String requester = "foo";
        map.put(X_REQUESTED_WITH, requester);
        mOnUiThread.loadUrlAndWaitForCompletion(url, Collections.unmodifiableMap(map));

        // verify that the request also includes X-Requested-With header
        // but is not overwritten by the webview
        HttpRequest request = mWebServer.getLastRequest(TestHtmlConstants.HELLO_WORLD_URL);
        Header[] matchingHeaders = request.getHeaders(X_REQUESTED_WITH);
        assertEquals(1, matchingHeaders.length);

        Header header = matchingHeaders[0];
        assertEquals(requester, header.getValue());
    }

    public void testCanInjectHeaders() throws Exception {
        final String X_FOO = "X-foo";
        final String X_FOO_VALUE = "test";

        final String X_REFERER = "Referer";
        final String X_REFERER_VALUE = "http://www.example.com/";
        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(X_FOO, X_FOO_VALUE);
        map.put(X_REFERER, X_REFERER_VALUE);
        mOnUiThread.loadUrlAndWaitForCompletion(url, map);

        HttpRequest request = mWebServer.getLastRequest(TestHtmlConstants.HELLO_WORLD_URL);
        for (Map.Entry<String,String> value : map.entrySet()) {
            String header = value.getKey();
            Header[] matchingHeaders = request.getHeaders(header);
            assertEquals("header " + header + " not found", 1, matchingHeaders.length);
            assertEquals(value.getValue(), matchingHeaders[0].getValue());
        }
    }

    @SuppressWarnings("deprecation")
    @UiThreadTest
    public void testGetVisibleTitleHeight() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(0, mWebView.getVisibleTitleHeight());
    }

    @UiThreadTest
    public void testGetOriginalUrl() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        startWebServer(false);
        final String finalUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final String redirectUrl =
                mWebServer.getRedirectingAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);

        assertNull(mWebView.getUrl());
        assertNull(mWebView.getOriginalUrl());

        // By default, WebView sends an intent to ask the system to
        // handle loading a new URL. We set a WebViewClient as
        // WebViewClient.shouldOverrideUrlLoading() returns false, so
        // the WebView will load the new URL.
        mOnUiThread.setWebViewClient(new WaitForLoadedClient(mOnUiThread));
        mOnUiThread.loadUrlAndWaitForCompletion(redirectUrl);

        assertEquals(finalUrl, mWebView.getUrl());
        assertEquals(redirectUrl, mWebView.getOriginalUrl());
    }

    public void testStopLoading() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        assertEquals(INITIAL_PROGRESS, mOnUiThread.getProgress());

        startWebServer(false);
        String url = mWebServer.getDelayedAssetUrl(TestHtmlConstants.STOP_LOADING_URL);

        class JsInterface {
            private boolean mPageLoaded;

            @JavascriptInterface
            public synchronized void pageLoaded() {
                mPageLoaded = true;
                notify();
            }
            public synchronized boolean getPageLoaded() {
                return mPageLoaded;
            }
        }

        JsInterface jsInterface = new JsInterface();

        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.addJavascriptInterface(jsInterface, "javabridge");
        mOnUiThread.loadUrl(url);
        mOnUiThread.stopLoading();

        // We wait to see that the onload callback in the HTML is not fired.
        synchronized (jsInterface) {
            jsInterface.wait(3000);
        }

        assertFalse(jsInterface.getPageLoaded());
    }

    @UiThreadTest
    public void testGoBackAndForward() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        assertGoBackOrForwardBySteps(false, -1);
        assertGoBackOrForwardBySteps(false, 1);

        startWebServer(false);
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

        mOnUiThread.loadUrlAndWaitForCompletion(url1);
        pollingCheckWebBackForwardList(url1, 0, 1);
        assertGoBackOrForwardBySteps(false, -1);
        assertGoBackOrForwardBySteps(false, 1);

        mOnUiThread.loadUrlAndWaitForCompletion(url2);
        pollingCheckWebBackForwardList(url2, 1, 2);
        assertGoBackOrForwardBySteps(true, -1);
        assertGoBackOrForwardBySteps(false, 1);

        mOnUiThread.loadUrlAndWaitForCompletion(url3);
        pollingCheckWebBackForwardList(url3, 2, 3);
        assertGoBackOrForwardBySteps(true, -2);
        assertGoBackOrForwardBySteps(false, 1);

        mWebView.goBack();
        pollingCheckWebBackForwardList(url2, 1, 3);
        assertGoBackOrForwardBySteps(true, -1);
        assertGoBackOrForwardBySteps(true, 1);

        mWebView.goForward();
        pollingCheckWebBackForwardList(url3, 2, 3);
        assertGoBackOrForwardBySteps(true, -2);
        assertGoBackOrForwardBySteps(false, 1);

        mWebView.goBackOrForward(-2);
        pollingCheckWebBackForwardList(url1, 0, 3);
        assertGoBackOrForwardBySteps(false, -1);
        assertGoBackOrForwardBySteps(true, 2);

        mWebView.goBackOrForward(2);
        pollingCheckWebBackForwardList(url3, 2, 3);
        assertGoBackOrForwardBySteps(true, -2);
        assertGoBackOrForwardBySteps(false, 1);
    }

    @UiThreadTest
    public void testAddJavascriptInterface() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        final class DummyJavaScriptInterface {
            private boolean mWasProvideResultCalled;
            private String mResult;

            private synchronized String waitForResult() {
                while (!mWasProvideResultCalled) {
                    try {
                        wait(TEST_TIMEOUT);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    if (!mWasProvideResultCalled) {
                        Assert.fail("Unexpected timeout");
                    }
                }
                return mResult;
            }

            public synchronized boolean wasProvideResultCalled() {
                return mWasProvideResultCalled;
            }

            @JavascriptInterface
            public synchronized void provideResult(String result) {
                mWasProvideResultCalled = true;
                mResult = result;
                notify();
            }
        }

        final DummyJavaScriptInterface obj = new DummyJavaScriptInterface();
        mWebView.addJavascriptInterface(obj, "dummy");
        assertFalse(obj.wasProvideResultCalled());

        startWebServer(false);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.ADD_JAVA_SCRIPT_INTERFACE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("Original title", obj.waitForResult());

        // Verify that only methods annotated with @JavascriptInterface are exposed
        // on the JavaScript interface object.
        mOnUiThread.evaluateJavascript("typeof dummy.provideResult",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String result) {
                        assertEquals("\"function\"", result);
                    }
                });
        mOnUiThread.evaluateJavascript("typeof dummy.wasProvideResultCalled",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String result) {
                        assertEquals("\"undefined\"", result);
                    }
                });
        mOnUiThread.evaluateJavascript("typeof dummy.getClass",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String result) {
                        assertEquals("\"undefined\"", result);
                    }
                });
    }

    @UiThreadTest
    public void testAddJavascriptInterfaceNullObject() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        String setTitleToPropertyTypeHtml = "<html><head></head>" +
                "<body onload=\"document.title = typeof window.injectedObject;\"></body></html>";

        // Test that the property is initially undefined.
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("undefined", mWebView.getTitle());

        // Test that adding a null object has no effect.
        mWebView.addJavascriptInterface(null, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("undefined", mWebView.getTitle());

        // Test that adding an object gives an object type.
        final Object obj = new Object();
        mWebView.addJavascriptInterface(obj, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("object", mWebView.getTitle());

        // Test that trying to replace with a null object has no effect.
        mWebView.addJavascriptInterface(null, "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("object", mWebView.getTitle());
    }

    @UiThreadTest
    public void testRemoveJavascriptInterface() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        String setTitleToPropertyTypeHtml = "<html><head></head>" +
                "<body onload=\"document.title = typeof window.injectedObject;\"></body></html>";

        // Test that adding an object gives an object type.
        mWebView.addJavascriptInterface(new Object(), "injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("object", mWebView.getTitle());

        // Test that reloading the page after removing the object leaves the property undefined.
        mWebView.removeJavascriptInterface("injectedObject");
        mOnUiThread.loadDataAndWaitForCompletion(setTitleToPropertyTypeHtml,
                "text/html", null);
        assertEquals("undefined", mWebView.getTitle());
    }

    public void testUseRemovedJavascriptInterface() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        class RemovedObject {
            @Override
            @JavascriptInterface
            public String toString() {
                return "removedObject";
            }

            @JavascriptInterface
            public void remove() throws Throwable {
                mOnUiThread.removeJavascriptInterface("removedObject");
                System.gc();
            }
        }
        class ResultObject {
            private String mResult;
            private boolean mIsResultAvailable;

            @JavascriptInterface
            public synchronized void setResult(String result) {
                mResult = result;
                mIsResultAvailable = true;
                notify();
            }
            public synchronized String getResult() {
                while (!mIsResultAvailable) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
                return mResult;
            }
        }
        final ResultObject resultObject = new ResultObject();

        // Test that an object is still usable if removed while the page is in use, even if we have
        // no external references to it.
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.addJavascriptInterface(new RemovedObject(), "removedObject");
        mOnUiThread.addJavascriptInterface(resultObject, "resultObject");
        mOnUiThread.loadDataAndWaitForCompletion("<html><head></head>" +
                "<body onload=\"window.removedObject.remove();" +
                "resultObject.setResult(removedObject.toString());\"></body></html>",
                "text/html", null);
        assertEquals("removedObject", resultObject.getResult());
    }

    public void testAddJavascriptInterfaceExceptions() throws Exception {
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        final AtomicBoolean mJsInterfaceWasCalled = new AtomicBoolean(false) {
            @JavascriptInterface
            public synchronized void call() {
                set(true);
                // The main purpose of this test is to ensure an exception here does not
                // crash the implementation.
                throw new RuntimeException("Javascript Interface exception");
            }
        };

        mOnUiThread.addJavascriptInterface(mJsInterfaceWasCalled, "dummy");

        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        assertFalse(mJsInterfaceWasCalled.get());

        final CountDownLatch resultLatch = new CountDownLatch(1);
        mOnUiThread.evaluateJavascript(
                "try {dummy.call(); 'fail'; } catch (exception) { 'pass'; } ",
                new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String result) {
                            assertEquals("\"pass\"", result);
                            resultLatch.countDown();
                        }
                    });

        assertTrue(resultLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(mJsInterfaceWasCalled.get());
    }

    public void testJavascriptInterfaceCustomPropertiesClearedOnReload() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        mOnUiThread.getSettings().setJavaScriptEnabled(true);

        class DummyJavaScriptInterface {
        }
        final DummyJavaScriptInterface obj = new DummyJavaScriptInterface();
        mOnUiThread.addJavascriptInterface(obj, "dummy");
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        EvaluateJsResultPollingCheck jsResult;
        jsResult = new EvaluateJsResultPollingCheck("42");
        mOnUiThread.evaluateJavascript("dummy.custom_property = 42", jsResult);
        jsResult.run();
        jsResult = new EvaluateJsResultPollingCheck("true");
        mOnUiThread.evaluateJavascript("'custom_property' in dummy", jsResult);
        jsResult.run();

        mOnUiThread.reload();

        jsResult = new EvaluateJsResultPollingCheck("false");
        mOnUiThread.evaluateJavascript("'custom_property' in dummy", jsResult);
        jsResult.run();
    }

    private final class TestPictureListener implements PictureListener {
        public int callCount;

        @Override
        public void onNewPicture(WebView view, Picture picture) {
            // Need to inform the listener tracking new picture
            // for the "page loaded" knowledge since it has been replaced.
            mOnUiThread.onNewPicture();
            this.callCount += 1;
        }
    }

    private Picture waitForPictureToHaveColor(int color,
            final TestPictureListener listener) throws Throwable {
        final int MAX_ON_NEW_PICTURE_ITERATIONS = 5;
        final AtomicReference<Picture> pictureRef = new AtomicReference<Picture>();
        for (int i = 0; i < MAX_ON_NEW_PICTURE_ITERATIONS; i++) {
            final int oldCallCount = listener.callCount;
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pictureRef.set(mWebView.capturePicture());
                }
            });
            if (isPictureFilledWithColor(pictureRef.get(), color))
                break;
            new PollingCheck(TEST_TIMEOUT) {
                @Override
                protected boolean check() {
                    return listener.callCount > oldCallCount;
                }
            }.run();
        }
        return pictureRef.get();
    }

    public void testCapturePicture() throws Exception, Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final TestPictureListener listener = new TestPictureListener();

        startWebServer(false);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.BLANK_PAGE_URL);
        mOnUiThread.setPictureListener(listener);
        // Showing the blank page will fill the picture with the background color.
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // The default background color is white.
        Picture oldPicture = waitForPictureToHaveColor(Color.WHITE, listener);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.setBackgroundColor(Color.CYAN);
            }
        });
        mOnUiThread.reloadAndWaitForCompletion();
        waitForPictureToHaveColor(Color.CYAN, listener);

        // The content of the previously captured picture will not be updated automatically.
        assertTrue(isPictureFilledWithColor(oldPicture, Color.WHITE));
    }

    public void testSetPictureListener() throws Exception, Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MyPictureListener implements PictureListener {
            public int callCount;
            public WebView webView;
            public Picture picture;

            @Override
            public void onNewPicture(WebView view, Picture picture) {
                // Need to inform the listener tracking new picture
                // for the "page loaded" knowledge since it has been replaced.
                mOnUiThread.onNewPicture();
                this.callCount += 1;
                this.webView = view;
                this.picture = picture;
            }
        }

        final MyPictureListener listener = new MyPictureListener();
        startWebServer(false);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setPictureListener(listener);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return listener.callCount > 0;
            }
        }.run();
        assertEquals(mWebView, listener.webView);
        assertNull(listener.picture);

        final int oldCallCount = listener.callCount;
        final String newUrl = mWebServer.getAssetUrl(TestHtmlConstants.SMALL_IMG_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(newUrl);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return listener.callCount > oldCallCount;
            }
        }.run();
    }

    @UiThreadTest
    public void testAccessHttpAuthUsernamePassword() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        try {
            WebViewDatabase.getInstance(getActivity()).clearHttpAuthUsernamePassword();

            String host = "http://localhost:8080";
            String realm = "testrealm";
            String userName = "user";
            String password = "password";

            String[] result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNull(result);

            mWebView.setHttpAuthUsernamePassword(host, realm, userName, password);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertEquals(userName, result[0]);
            assertEquals(password, result[1]);

            String newPassword = "newpassword";
            mWebView.setHttpAuthUsernamePassword(host, realm, userName, newPassword);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertEquals(userName, result[0]);
            assertEquals(newPassword, result[1]);

            String newUserName = "newuser";
            mWebView.setHttpAuthUsernamePassword(host, realm, newUserName, newPassword);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertEquals(newUserName, result[0]);
            assertEquals(newPassword, result[1]);

            // the user is set to null, can not change any thing in the future
            mWebView.setHttpAuthUsernamePassword(host, realm, null, password);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertNull(result[0]);
            assertEquals(password, result[1]);

            mWebView.setHttpAuthUsernamePassword(host, realm, userName, null);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertEquals(userName, result[0]);
            assertEquals(null, result[1]);

            mWebView.setHttpAuthUsernamePassword(host, realm, null, null);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertNull(result[0]);
            assertNull(result[1]);

            mWebView.setHttpAuthUsernamePassword(host, realm, newUserName, newPassword);
            result = mWebView.getHttpAuthUsernamePassword(host, realm);
            assertNotNull(result);
            assertEquals(newUserName, result[0]);
            assertEquals(newPassword, result[1]);
        } finally {
            WebViewDatabase.getInstance(getActivity()).clearHttpAuthUsernamePassword();
        }
    }

    public void testLoadData() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final String HTML_CONTENT =
                "<html><head><title>Hello,World!</title></head><body></body>" +
                "</html>";
        mOnUiThread.loadDataAndWaitForCompletion(HTML_CONTENT,
                "text/html", null);
        assertEquals("Hello,World!", mOnUiThread.getTitle());

        startWebServer(false);
        final ChromeClient webChromeClient = new ChromeClient(mOnUiThread);
        final String crossOriginUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.getSettings().setJavaScriptEnabled(true);
                mOnUiThread.setWebChromeClient(webChromeClient);
                mOnUiThread.loadDataAndWaitForCompletion(
                        "<html><head></head><body onload=\"" +
                        "document.title = " +
                        "document.getElementById('frame').contentWindow.location.href;" +
                        "\"><iframe id=\"frame\" src=\"" + crossOriginUrl + "\"></body></html>",
                        "text/html", null);
            }
        });
        assertEquals(ConsoleMessage.MessageLevel.ERROR, webChromeClient.getMessageLevel(10000));
    }

    @UiThreadTest
    public void testLoadDataWithBaseUrl() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        assertNull(mWebView.getUrl());
        String imgUrl = TestHtmlConstants.SMALL_IMG_URL; // relative
        // Snippet of HTML that will prevent favicon requests to the test server.
        final String HTML_HEADER = "<html><head><link rel=\"shortcut icon\" href=\"#\" /></head>";

        // Trying to resolve a relative URL against a data URL without a base URL
        // will fail and we won't make a request to the test web server.
        // By using the test web server as the base URL we expect to see a request
        // for the relative URL in the test server.
        startWebServer(false);
        String baseUrl = mWebServer.getAssetUrl("foo.html");
        String historyUrl = "http://www.example.com/";
        mWebServer.resetRequestState();
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(baseUrl,
                HTML_HEADER + "<body><img src=\"" + imgUrl + "\"/></body></html>",
                "text/html", "UTF-8", historyUrl);
        // Verify that the resource request makes it to the server.
        assertTrue(mWebServer.wasResourceRequested(imgUrl));
        assertEquals(historyUrl, mWebView.getUrl());

        // Check that reported URL is "about:blank" when supplied history URL
        // is null.
        imgUrl = TestHtmlConstants.LARGE_IMG_URL;
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(baseUrl,
                HTML_HEADER + "<body><img src=\"" + imgUrl + "\"/></body></html>",
                "text/html", "UTF-8", null);
        assertTrue(mWebServer.wasResourceRequested(imgUrl));
        assertEquals("about:blank", mWebView.getUrl());

        // Test that JavaScript can access content from the same origin as the base URL.
        mWebView.getSettings().setJavaScriptEnabled(true);
        final String crossOriginUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(baseUrl,
                HTML_HEADER + "<body onload=\"" +
                "document.title = document.getElementById('frame').contentWindow.location.href;" +
                "\"><iframe id=\"frame\" src=\"" + crossOriginUrl + "\"></body></html>",
                "text/html", "UTF-8", null);
        assertEquals(crossOriginUrl, mWebView.getTitle());

        // Check that when the base URL uses the 'data' scheme, a 'data' scheme URL is used and the
        // history URL is ignored.
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion("data:foo",
                HTML_HEADER + "<body>bar</body></html>", "text/html", "UTF-8",
                historyUrl);
        assertTrue("URL: " + mWebView.getUrl(), mWebView.getUrl().indexOf("data:text/html") == 0);
        assertTrue("URL: " + mWebView.getUrl(), mWebView.getUrl().indexOf("bar") > 0);

        // Check that when a non-data: base URL is used, we treat the String to load as
        // a raw string and just dump it into the WebView, i.e. not decoding any URL entities.
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion("http://www.foo.com",
                HTML_HEADER + "<title>Hello World%21</title><body>bar</body></html>",
                "text/html", "UTF-8", null);
        assertEquals("Hello World%21", mOnUiThread.getTitle());

        // Check that when a data: base URL is used, we treat the String to load as a data: URL
        // and run load steps such as decoding URL entities (i.e., contrary to the test case
        // above.)
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion("data:foo",
                HTML_HEADER + "<title>Hello World%21</title></html>", "text/html", "UTF-8", null);
        assertEquals("Hello World!", mOnUiThread.getTitle());

        // Check the method is null input safe.
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(null, null, null, null, null);
        assertEquals("about:blank", mOnUiThread.getUrl());
    }

    private static class WaitForFindResultsListener extends FutureTask<Integer>
            implements WebView.FindListener {
        public WaitForFindResultsListener() {
            super(new Runnable() {
                @Override
                public void run() { }
            }, null);
        }

        @Override
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
                boolean isDoneCounting) {
            if (isDoneCounting) {
                set(numberOfMatches);
            }
        }
    }

    public void testFindAll()  throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Make the page scrollable, so we can detect the scrolling to make sure the
        // content fully loaded.
        mOnUiThread.setInitialScale(100);
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = Math.max(metrics.widthPixels, metrics.heightPixels);
        // create a paragraph high enough to take up the entire screen
        String p = "<p style=\"height:" + dimension + "px;\">" +
                "Find all instances of find on the page and highlight them.</p>";

        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "</body></html>", "text/html", null);

        WaitForFindResultsListener l = new WaitForFindResultsListener();
        int previousScrollY = mOnUiThread.getScrollY();
        mOnUiThread.pageDown(true);
        // Wait for content fully loaded.
        waitForScrollingComplete(previousScrollY);
        mOnUiThread.setFindListener(l);
        mOnUiThread.findAll("find");

        assertEquals(2, l.get().intValue());
    }

    public void testFindNext() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Reset the scaling so that finding the next "all" text will require scrolling.
        mOnUiThread.setInitialScale(100);

        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = Math.max(metrics.widthPixels, metrics.heightPixels);
        // create a paragraph high enough to take up the entire screen
        String p = "<p style=\"height:" + dimension + "px;\">" +
                "Find all instances of a word on the page and highlight them.</p>";

        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p + p + "</body></html>", "text/html", null);

        // highlight all the strings found
        mOnUiThread.findAll("all");
        getInstrumentation().waitForIdleSync();

        int previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the second page and assert that the view scrolls.
        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() > previousScrollY);
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the first page and assert that the view scrolls.
        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() < previousScrollY);
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the second page and assert that the view scrolls.
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() > previousScrollY);
        previousScrollY = mOnUiThread.getScrollY();

        // Focus "all" in the first page and assert that the view scrolls.
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() < previousScrollY);
        previousScrollY = mOnUiThread.getScrollY();

        // clear the result
        mOnUiThread.clearMatches();
        getInstrumentation().waitForIdleSync();

        // can not scroll any more
        mOnUiThread.findNext(false);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() == previousScrollY);

        mOnUiThread.findNext(true);
        waitForScrollingComplete(previousScrollY);
        assertTrue(mOnUiThread.getScrollY() == previousScrollY);
    }

    public void testDocumentHasImages() throws Exception, Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class DocumentHasImageCheckHandler extends Handler {
            private boolean mReceived;
            private int mMsgArg1;
            public DocumentHasImageCheckHandler(Looper looper) {
                super(looper);
            }
            @Override
            public void handleMessage(Message msg) {
                synchronized(this) {
                    mReceived = true;
                    mMsgArg1 = msg.arg1;
                }
            }
            public synchronized boolean hasCalledHandleMessage() {
                return mReceived;
            }
            public synchronized int getMsgArg1() {
                return mMsgArg1;
            }
        }

        startWebServer(false);
        final String imgUrl = mWebServer.getAssetUrl(TestHtmlConstants.SMALL_IMG_URL);

        // Create a handler on the UI thread.
        final DocumentHasImageCheckHandler handler =
            new DocumentHasImageCheckHandler(mWebView.getHandler().getLooper());

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOnUiThread.loadDataAndWaitForCompletion("<html><body><img src=\""
                        + imgUrl + "\"/></body></html>", "text/html", null);
                Message response = new Message();
                response.setTarget(handler);
                assertFalse(handler.hasCalledHandleMessage());
                mWebView.documentHasImages(response);
            }
        });
        new PollingCheck() {
            @Override
            protected boolean check() {
                return handler.hasCalledHandleMessage();
            }
        }.run();
        assertEquals(1, handler.getMsgArg1());
    }

    public void testPageScroll() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        int dimension = 2 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p = "<p style=\"height:" + dimension + "px;\">" +
                "Scroll by half the size of the page.</p>";
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + p + "</body></html>", "text/html", null);

        // Wait for UI thread to settle and receive page dimentions from renderer
        // such that we can invoke page down.
        new PollingCheck() {
            @Override
            protected boolean check() {
                 return mOnUiThread.pageDown(false);
            }
        }.run();

        do {
            getInstrumentation().waitForIdleSync();
        } while (mOnUiThread.pageDown(false));

        getInstrumentation().waitForIdleSync();
        int bottomScrollY = mOnUiThread.getScrollY();

        assertTrue(mOnUiThread.pageUp(false));

        do {
            getInstrumentation().waitForIdleSync();
        } while (mOnUiThread.pageUp(false));

        getInstrumentation().waitForIdleSync();
        int topScrollY = mOnUiThread.getScrollY();

        // jump to the bottom
        assertTrue(mOnUiThread.pageDown(true));
        getInstrumentation().waitForIdleSync();
        assertEquals(bottomScrollY, mOnUiThread.getScrollY());

        // jump to the top
        assertTrue(mOnUiThread.pageUp(true));
        getInstrumentation().waitForIdleSync();
        assertEquals(topScrollY, mOnUiThread.getScrollY());
    }

    public void testGetContentHeight() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body></body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mOnUiThread.getScale() != 0 && mOnUiThread.getContentHeight() != 0
                    && mOnUiThread.getHeight() != 0;
            }
        }.run();
        assertEquals(mOnUiThread.getHeight(),
                mOnUiThread.getContentHeight() * mOnUiThread.getScale(), 2f);

        final int pageHeight = 600;
        // set the margin to 0
        final String p = "<p style=\"height:" + pageHeight
                + "px;margin:0px auto;\">Get the height of HTML content.</p>";
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "</body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() > pageHeight;
            }
        }.run();

        final int extraSpace = mOnUiThread.getContentHeight() - pageHeight;
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + p + "</body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return pageHeight + pageHeight + extraSpace == mOnUiThread.getContentHeight();
            }
        }.run();
    }

    @UiThreadTest
    public void testPlatformNotifications() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        WebView.enablePlatformNotifications();
        WebView.disablePlatformNotifications();
    }

    @UiThreadTest
    public void testAccessPluginList() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        assertNotNull(WebView.getPluginList());

        // can not find a way to install plugins
        mWebView.refreshPlugins(false);
    }

    @UiThreadTest
    public void testDestroy() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Create a new WebView, since we cannot call destroy() on a view in the hierarchy
        WebView localWebView = new WebView(getActivity());
        localWebView.destroy();
    }

    public void testFlingScroll() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        final int dimension = 10 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p = "<p style=\"height:" + dimension + "px;" +
                "width:" + dimension + "px\">Test fling scroll.</p>";
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "</body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() >= dimension;
            }
        }.run();
        getInstrumentation().waitForIdleSync();

        final int previousScrollX = mOnUiThread.getScrollX();
        final int previousScrollY = mOnUiThread.getScrollY();

        mOnUiThread.flingScroll(100, 100);

        new PollingCheck() {
            @Override
            protected boolean check() {
                return mOnUiThread.getScrollX() > previousScrollX &&
                        mOnUiThread.getScrollY() > previousScrollY;
            }
        }.run();
    }

    public void testRequestFocusNodeHref() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        startWebServer(false);
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        final String links = "<DL><p><DT><A HREF=\"" + url1
                + "\">HTML_URL1</A><DT><A HREF=\"" + url2
                + "\">HTML_URL2</A></DL><p>";
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + links + "</body></html>", "text/html", null);
        getInstrumentation().waitForIdleSync();

        final HrefCheckHandler handler = new HrefCheckHandler(mWebView.getHandler().getLooper());
        final Message hrefMsg = new Message();
        hrefMsg.setTarget(handler);

        // focus on first link
        handler.reset();
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        mOnUiThread.requestFocusNodeHref(hrefMsg);
        new PollingCheck() {
            @Override
            protected boolean check() {
                boolean done = false;
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestFocusNodeHref(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(url1, handler.getResultUrl());

        // focus on second link
        handler.reset();
        final Message hrefMsg2 = new Message();
        hrefMsg2.setTarget(handler);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        mOnUiThread.requestFocusNodeHref(hrefMsg2);
        new PollingCheck() {
            @Override
            protected boolean check() {
                boolean done = false;
                final String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null &&
                            handler.mResultUrl.equals(url2)) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestFocusNodeHref(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(url2, handler.getResultUrl());

        mOnUiThread.requestFocusNodeHref(null);
    }

    public void testRequestImageRef() throws Exception, Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class ImageLoaded {
            public boolean mImageLoaded;

            @JavascriptInterface
            public void loaded() {
                mImageLoaded = true;
            }
        }
        final ImageLoaded imageLoaded = new ImageLoaded();
        runTestOnUiThread(new Runnable() {
            public void run() {
                mOnUiThread.getSettings().setJavaScriptEnabled(true);
            }
        });
        mOnUiThread.addJavascriptInterface(imageLoaded, "imageLoaded");
        AssetManager assets = getActivity().getAssets();
        Bitmap bitmap = BitmapFactory.decodeStream(assets.open(TestHtmlConstants.LARGE_IMG_URL));
        int imgWidth = bitmap.getWidth();
        int imgHeight = bitmap.getHeight();

        startWebServer(false);
        final String imgUrl = mWebServer.getAssetUrl(TestHtmlConstants.LARGE_IMG_URL);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><head><title>Title</title><style type=\"text/css\">"
                + "#imgElement { -webkit-transform: translate3d(0,0,1); }"
                + "#imgElement.finish { -webkit-transform: translate3d(0,0,0);"
                + " -webkit-transition-duration: 1ms; }</style>"
                + "<script type=\"text/javascript\">function imgLoad() {"
                + "imgElement = document.getElementById('imgElement');"
                + "imgElement.addEventListener('webkitTransitionEnd',"
                + "function(e) { imageLoaded.loaded(); });"
                + "imgElement.className = 'finish';}</script>"
                + "</head><body><img id=\"imgElement\" src=\"" + imgUrl
                + "\" width=\"" + imgWidth + "\" height=\"" + imgHeight
                + "\" onLoad=\"imgLoad()\"/></body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return imageLoaded.mImageLoaded;
            }
        }.run();
        getInstrumentation().waitForIdleSync();

        final HrefCheckHandler handler = new HrefCheckHandler(mWebView.getHandler().getLooper());
        final Message msg = new Message();
        msg.setTarget(handler);

        // touch the image
        handler.reset();
        int[] location = mOnUiThread.getLocationOnScreen();

        long time = SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(
                MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                        location[0] + imgWidth / 2,
                        location[1] + imgHeight / 2, 0));
        getInstrumentation().waitForIdleSync();
        mOnUiThread.requestImageRef(msg);
        new PollingCheck() {
            @Override
            protected boolean check() {
                boolean done = false;
                if (handler.hasCalledHandleMessage()) {
                    if (handler.mResultUrl != null) {
                        done = true;
                    } else {
                        handler.reset();
                        Message newMsg = new Message();
                        newMsg.setTarget(handler);
                        mOnUiThread.requestImageRef(newMsg);
                    }
                }
                return done;
            }
        }.run();
        assertEquals(imgUrl, handler.mResultUrl);
    }

    @UiThreadTest
    public void testDebugDump() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebView.debugDump();
    }

    public void testGetHitTestResult() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final String anchor = "<p><a href=\"" + TestHtmlConstants.EXT_WEB_URL1
                + "\">normal anchor</a></p>";
        final String blankAnchor = "<p><a href=\"\">blank anchor</a></p>";
        final String form = "<p><form><input type=\"text\" name=\"Test\"><br>"
                + "<input type=\"submit\" value=\"Submit\"></form></p>";
        String phoneNo = "3106984000";
        final String tel = "<p><a href=\"tel:" + phoneNo + "\">Phone</a></p>";
        String email = "test@gmail.com";
        final String mailto = "<p><a href=\"mailto:" + email + "\">Email</a></p>";
        String location = "shanghai";
        final String geo = "<p><a href=\"geo:0,0?q=" + location + "\">Location</a></p>";

        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion("fake://home",
                "<html><body>" + anchor + blankAnchor + form + tel + mailto +
                geo + "</body></html>", "text/html", "UTF-8", null);
        getInstrumentation().waitForIdleSync();

        // anchor
        moveFocusDown();
        HitTestResult hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.SRC_ANCHOR_TYPE, hitTestResult.getType());
        assertEquals(TestHtmlConstants.EXT_WEB_URL1, hitTestResult.getExtra());

        // blank anchor
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.SRC_ANCHOR_TYPE, hitTestResult.getType());
        assertEquals("fake://home", hitTestResult.getExtra());

        // text field
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.EDIT_TEXT_TYPE, hitTestResult.getType());
        assertNull(hitTestResult.getExtra());

        // submit button
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.UNKNOWN_TYPE, hitTestResult.getType());
        assertNull(hitTestResult.getExtra());

        // phone number
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.PHONE_TYPE, hitTestResult.getType());
        assertEquals(phoneNo, hitTestResult.getExtra());

        // email
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.EMAIL_TYPE, hitTestResult.getType());
        assertEquals(email, hitTestResult.getExtra());

        // geo address
        moveFocusDown();
        hitTestResult = mOnUiThread.getHitTestResult();
        assertEquals(HitTestResult.GEO_TYPE, hitTestResult.getType());
        assertEquals(location, hitTestResult.getExtra());
    }

    public void testSetInitialScale() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final String p = "<p style=\"height:1000px;width:1000px\">Test setInitialScale.</p>";
        final float defaultScale =
            getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density;

        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "</body></html>", "text/html", null);

        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(0);
        // modify content to fool WebKit into re-loading
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "2" + "</body></html>", "text/html", null);

        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(50);
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "3" + "</body></html>", "text/html", null);

        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return Math.abs(0.5 - mOnUiThread.getScale()) < .01f;
            }
        }.run();

        mOnUiThread.setInitialScale(0);
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "4" + "</body></html>", "text/html", null);

        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return Math.abs(defaultScale - mOnUiThread.getScale()) < .01f;
            }
        }.run();
    }

    @UiThreadTest
    public void testClearHistory() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        startWebServer(false);
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

        mOnUiThread.loadUrlAndWaitForCompletion(url1);
        pollingCheckWebBackForwardList(url1, 0, 1);

        mOnUiThread.loadUrlAndWaitForCompletion(url2);
        pollingCheckWebBackForwardList(url2, 1, 2);

        mOnUiThread.loadUrlAndWaitForCompletion(url3);
        pollingCheckWebBackForwardList(url3, 2, 3);

        mWebView.clearHistory();

        // only current URL is left after clearing
        pollingCheckWebBackForwardList(url3, 0, 1);
    }

    @UiThreadTest
    public void testSaveAndRestoreState() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // nothing to save
        assertNull(mWebView.saveState(new Bundle()));

        startWebServer(false);
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        String url2 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        String url3 = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL3);

        // make a history list
        mOnUiThread.loadUrlAndWaitForCompletion(url1);
        pollingCheckWebBackForwardList(url1, 0, 1);
        mOnUiThread.loadUrlAndWaitForCompletion(url2);
        pollingCheckWebBackForwardList(url2, 1, 2);
        mOnUiThread.loadUrlAndWaitForCompletion(url3);
        pollingCheckWebBackForwardList(url3, 2, 3);

        // save the list
        Bundle bundle = new Bundle();
        WebBackForwardList saveList = mWebView.saveState(bundle);
        assertNotNull(saveList);
        assertEquals(3, saveList.getSize());
        assertEquals(2, saveList.getCurrentIndex());
        assertEquals(url1, saveList.getItemAtIndex(0).getUrl());
        assertEquals(url2, saveList.getItemAtIndex(1).getUrl());
        assertEquals(url3, saveList.getItemAtIndex(2).getUrl());

        // change the content to a new "blank" web view without history
        final WebView newWebView = new WebView(getActivity());

        WebBackForwardList copyListBeforeRestore = newWebView.copyBackForwardList();
        assertNotNull(copyListBeforeRestore);
        assertEquals(0, copyListBeforeRestore.getSize());

        // restore the list
        final WebBackForwardList restoreList = newWebView.restoreState(bundle);
        assertNotNull(restoreList);
        assertEquals(3, restoreList.getSize());
        assertEquals(2, saveList.getCurrentIndex());

        // wait for the list items to get inflated
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return restoreList.getItemAtIndex(0).getUrl() != null &&
                       restoreList.getItemAtIndex(1).getUrl() != null &&
                       restoreList.getItemAtIndex(2).getUrl() != null;
            }
        }.run();
        assertEquals(url1, restoreList.getItemAtIndex(0).getUrl());
        assertEquals(url2, restoreList.getItemAtIndex(1).getUrl());
        assertEquals(url3, restoreList.getItemAtIndex(2).getUrl());

        WebBackForwardList copyListAfterRestore = newWebView.copyBackForwardList();
        assertNotNull(copyListAfterRestore);
        assertEquals(3, copyListAfterRestore.getSize());
        assertEquals(2, copyListAfterRestore.getCurrentIndex());
        assertEquals(url1, copyListAfterRestore.getItemAtIndex(0).getUrl());
        assertEquals(url2, copyListAfterRestore.getItemAtIndex(1).getUrl());
        assertEquals(url3, copyListAfterRestore.getItemAtIndex(2).getUrl());
    }

    public void testSetWebViewClient() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final ScaleChangedWebViewClient webViewClient = new ScaleChangedWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        startWebServer(false);

        assertFalse(webViewClient.onScaleChangedCalled());
        String url1 = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url1);
        pollingCheckForCanZoomIn();

        assertTrue(mOnUiThread.zoomIn());
        webViewClient.waitForScaleChanged();
    }

    @UiThreadTest
    public void testInsecureSiteClearsCertificate() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(true);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        SslCertificate cert = mWebView.getCertificate();
        assertNotNull(cert);
        assertEquals("Android", cert.getIssuedTo().getUName());

        stopWebServer();

        startWebServer(false);
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        assertNull(mWebView.getCertificate());
    }

    @UiThreadTest
    public void testSecureSiteSetsCertificate() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(false);
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        assertNull(mWebView.getCertificate());

        stopWebServer();

        startWebServer(true);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(
                mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL));
        SslCertificate cert = mWebView.getCertificate();
        assertNotNull(cert);
        assertEquals("Android", cert.getIssuedTo().getUName());
    }

    @UiThreadTest
    public void testClearSslPreferences() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient();
        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the page again. We expect another call to
        // WebViewClient.onReceivedSslError() since we cleared sslpreferences.
        mOnUiThread.clearSslPreferences();
        webViewClient.resetWasOnReceivedSslErrorCalled();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());

        // Load the page once again, without clearing the sslpreferences.
        // Make sure we do not get the callback.
        webViewClient.resetWasOnReceivedSslErrorCalled();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
    }

    public void testOnReceivedSslError() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            private String mErrorUrl;
            private WebView mWebView;

            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                mWebView = view;
                mErrorUrl = error.getUrl();
                handler.proceed();
            }
            public String errorUrl() {
                return mErrorUrl;
            }
            public WebView webView() {
                return mWebView;
            }
        }

        startWebServer(true);
        final String errorUrl = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final MockWebViewClient webViewClient = new MockWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(errorUrl);

        assertEquals(mWebView, webViewClient.webView());
        assertEquals(errorUrl, webViewClient.errorUrl());
    }

    public void testOnReceivedSslErrorProceed() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        }

        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
    }

    public void testOnReceivedSslErrorCancel() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebViewClient extends WaitForLoadedClient {
            public MockWebViewClient() {
                super(mOnUiThread);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        }

        startWebServer(true);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.setWebViewClient(new MockWebViewClient());
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
    }

    public void testSslErrorProceedResponseReusedForSameHost() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient();
        startWebServer(true);
        final String firstUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(firstUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the second page. We don't expect a call to
        // WebViewClient.onReceivedSslError(), but the page should load.
        webViewClient.resetWasOnReceivedSslErrorCalled();
        final String sameHostUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2);
        mOnUiThread.loadUrlAndWaitForCompletion(sameHostUrl);
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals("Second page", mOnUiThread.getTitle());
    }

    public void testSslErrorProceedResponseNotReusedForDifferentHost() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        // Load the first page. We expect a call to
        // WebViewClient.onReceivedSslError().
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient();
        startWebServer(true);
        final String firstUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(firstUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());

        // Load the second page. We expect another call to
        // WebViewClient.onReceivedSslError().
        webViewClient.resetWasOnReceivedSslErrorCalled();
        // The test server uses the host "localhost". "127.0.0.1" works as an
        // alias, but will be considered unique by the WebView.
        final String differentHostUrl = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL2).replace(
                "localhost", "127.0.0.1");
        mOnUiThread.loadUrlAndWaitForCompletion(differentHostUrl);
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals("Second page", mOnUiThread.getTitle());
    }

    public void testSecureServerRequestingClientCertDoesNotCancelRequest() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.WANTS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Page loaded OK...
        assertTrue(webViewClient.wasOnReceivedSslErrorCalled());
        assertEquals(TestHtmlConstants.HELLO_WORLD_TITLE, mOnUiThread.getTitle());
        assertEquals(0, webViewClient.onReceivedErrorCode());
    }

    public void testSecureServerRequiringClientCertDoesCancelRequest() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final SslErrorWebViewClient webViewClient = new SslErrorWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        mOnUiThread.clearSslPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Page NOT loaded OK...
        // In this case, we must NOT have received the onReceivedSslError callback as that is for
        // recoverable (e.g. server auth) errors, whereas failing mandatory client auth is non-
        // recoverable and should drop straight through to a load error.
        assertFalse(webViewClient.wasOnReceivedSslErrorCalled());
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
    }

    public void testProceedClientCertRequest() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));

        // Test that the user's response for this server is kept in cache. Load a different
        // page from the same server and make sure we don't receive a client cert request callback.
        int callCount = webViewClient.getClientCertRequestCount();
        url = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount, webViewClient.getClientCertRequestCount());

        // Now clear the cache and reload the page. We should receive a new callback.
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(callCount + 1, webViewClient.getClientCertRequestCount());
    }

    public void testIgnoreClientCertRequest() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        // Ignore the request. Load should fail.
        webViewClient.setAction(ClientCertWebViewClient.IGNORE);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());

        // Load a different page from the same domain, ignoring the request. We should get a callback,
        // and load should fail.
        int callCount = webViewClient.getClientCertRequestCount();
        url = mWebServer.getAssetUrl(TestHtmlConstants.HTML_URL1);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HTML_URL1_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
        assertEquals(callCount + 1, webViewClient.getClientCertRequestCount());

        // Reload, proceeding the request. Load should succeed.
        webViewClient.setAction(ClientCertWebViewClient.PROCEED);
        url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertTrue(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
    }

    public void testCancelClientCertRequest() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        // Cancel the request. Load should fail.
        webViewClient.setAction(ClientCertWebViewClient.CANCEL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());

        // Reload. The request should fail without generating a new callback.
        int callCount = webViewClient.getClientCertRequestCount();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals(callCount, webViewClient.getClientCertRequestCount());
        assertFalse(TestHtmlConstants.HELLO_WORLD_TITLE.equals(mOnUiThread.getTitle()));
        assertEquals(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, webViewClient.onReceivedErrorCode());
    }

    /**
     * {@link X509TrustManager} that trusts everybody.
     */
    private static class TrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Trust the CtSTestServer's client...
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Trust the CtSTestServer...
        }

        public X509Certificate[] getAcceptedIssuers() {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                return new X509Certificate[] {
                        (X509Certificate) certFactory.generateCertificate(
                                new ByteArrayInputStream(FAKE_RSA_CA_1))
                        };
            } catch (Exception ex) {
                Log.e(LOGTAG, "failed creating certificate chain" + ex);
                return null;
            }
        }
    }

    public void testClientCertIssuersReceivedCorrectly() throws Throwable {
        mWebServer = new CtsTestServer(getActivity(), CtsTestServer.SslMode.NEEDS_CLIENT_AUTH,
                new TrustManager());
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        final ClientCertWebViewClient webViewClient = new ClientCertWebViewClient();
        mOnUiThread.setWebViewClient(webViewClient);
        clearClientCertPreferences();
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        // Verify that issuers sent by the server are received correctly
        // TODO(sgurun) no need to create again, maybe make it a final instance variable.
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate  cert = (X509Certificate) certFactory.generateCertificate(
                                new ByteArrayInputStream(FAKE_RSA_CA_1));
        Principal[] principals = webViewClient.getPrincipals();
        assertEquals(1, principals.length);
        // TODO: should we issue getIssuerX500Principal instead?
        assertEquals(cert.getIssuerDN(), principals[0]);
    }

    private void clearClientCertPreferences() {
       final AtomicBoolean cleared = new AtomicBoolean(false);
        mOnUiThread.clearClientCertPreferences(new Runnable() {
            @Override
            public void run() {
                cleared.set(true);
            }
        });
        // Wait until clearclientcertpreferences clears the preferences. Generally this is just a
        // thread hopping.
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return cleared.get();
            }
        }.run();
    }

    public void testRequestChildRectangleOnScreen() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        DisplayMetrics metrics = mOnUiThread.getDisplayMetrics();
        final int dimension = 2 * Math.max(metrics.widthPixels, metrics.heightPixels);
        String p = "<p style=\"height:" + dimension + "px;width:" + dimension + "px\">&nbsp;</p>";
        mOnUiThread.loadDataAndWaitForCompletion("<html><body>" + p
                + "</body></html>", "text/html", null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mOnUiThread.getContentHeight() >= dimension;
            }
        }.run();

        int origX = mOnUiThread.getScrollX();
        int origY = mOnUiThread.getScrollY();

        int half = dimension / 2;
        Rect rect = new Rect(half, half, half + 1, half + 1);
        assertTrue(mOnUiThread.requestChildRectangleOnScreen(mWebView, rect, true));
        assertTrue(mOnUiThread.getScrollX() > origX);
        assertTrue(mOnUiThread.getScrollY() > origY);
    }

    public void testSetDownloadListener() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final CountDownLatch resultLatch = new CountDownLatch(1);
        final class MyDownloadListener implements DownloadListener {
            public String url;
            public String mimeType;
            public long contentLength;
            public String contentDisposition;

            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimetype, long contentLength) {
                this.url = url;
                this.mimeType = mimetype;
                this.contentLength = contentLength;
                this.contentDisposition = contentDisposition;
                resultLatch.countDown();
            }
        }

        final String mimeType = "application/octet-stream";
        final int length = 100;
        final MyDownloadListener listener = new MyDownloadListener();

        startWebServer(false);
        final String url = mWebServer.getBinaryUrl(mimeType, length);

        // By default, WebView sends an intent to ask the system to
        // handle loading a new URL. We set WebViewClient as
        // WebViewClient.shouldOverrideUrlLoading() returns false, so
        // the WebView will load the new URL.
        mOnUiThread.setDownloadListener(listener);
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body onload=\"window.location = \'" + url + "\'\"></body></html>",
                "text/html", null);
        // Wait for layout to complete before setting focus.
        getInstrumentation().waitForIdleSync();

        assertTrue(resultLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(url, listener.url);
        assertTrue(listener.contentDisposition.contains("test.bin"));
        assertEquals(length, listener.contentLength);
        assertEquals(mimeType, listener.mimeType);
    }

    @UiThreadTest
    public void testSetLayoutParams() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(600, 800);
        mWebView.setLayoutParams(params);
        assertSame(params, mWebView.getLayoutParams());
    }

    @UiThreadTest
    public void testSetMapTrackballToArrowKeys() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mWebView.setMapTrackballToArrowKeys(true);
    }

    public void testSetNetworkAvailable() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        startWebServer(false);

        String url = mWebServer.getAssetUrl(TestHtmlConstants.NETWORK_STATE_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("ONLINE", mOnUiThread.getTitle());

        mOnUiThread.setNetworkAvailable(false);

        // Wait for the DOM to receive notification of the network state change.
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals("OFFLINE");
            }
        }.run();

        mOnUiThread.setNetworkAvailable(true);

        // Wait for the DOM to receive notification of the network state change.
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals("ONLINE");
            }
        }.run();
    }

    public void testSetWebChromeClient() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        final class MockWebChromeClient extends WaitForProgressClient {
            private boolean mOnProgressChanged = false;

            public MockWebChromeClient() {
                super(mOnUiThread);
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                mOnProgressChanged = true;
            }
            public boolean onProgressChangedCalled() {
                return mOnProgressChanged;
            }
        }

        final MockWebChromeClient webChromeClient = new MockWebChromeClient();

        mOnUiThread.setWebChromeClient(webChromeClient);
        getInstrumentation().waitForIdleSync();
        assertFalse(webChromeClient.onProgressChangedCalled());

        startWebServer(false);
        final String url = mWebServer.getAssetUrl(TestHtmlConstants.HELLO_WORLD_URL);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        getInstrumentation().waitForIdleSync();

        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return webChromeClient.onProgressChangedCalled();
            }
        }.run();
    }

    public void testPauseResumeTimers() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        class Monitor {
            private boolean mIsUpdated;

            @JavascriptInterface
            public synchronized void update() {
                mIsUpdated  = true;
                notify();
            }
            public synchronized boolean waitForUpdate() {
                while (!mIsUpdated) {
                    try {
                        // This is slightly flaky, as we can't guarantee that
                        // this is a sufficient time limit, but there's no way
                        // around this.
                        wait(1000);
                        if (!mIsUpdated) {
                            return false;
                        }
                    } catch (InterruptedException e) {
                    }
                }
                mIsUpdated = false;
                return true;
            }
        };
        final Monitor monitor = new Monitor();
        final String updateMonitorHtml = "<html>" +
                "<body onload=\"monitor.update();\"></body></html>";

        // Test that JavaScript is executed even with timers paused.
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.getSettings().setJavaScriptEnabled(true);
                mWebView.addJavascriptInterface(monitor, "monitor");
                mWebView.pauseTimers();
                mOnUiThread.loadDataAndWaitForCompletion(updateMonitorHtml,
                        "text/html", null);
            }
        });
        assertTrue(monitor.waitForUpdate());

        // Start a timer and test that it does not fire.
        mOnUiThread.loadDataAndWaitForCompletion(
                "<html><body onload='setTimeout(function(){monitor.update();},100)'>" +
                "</body></html>", "text/html", null);
        assertFalse(monitor.waitForUpdate());

        // Resume timers and test that the timer fires.
        mOnUiThread.resumeTimers();
        assertTrue(monitor.waitForUpdate());
    }

    // verify query parameters can be passed correctly to android asset files
    public void testAndroidAssetQueryParam() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        // test passing a parameter
        String fileUrl = TestHtmlConstants.getFileUrl(TestHtmlConstants.PARAM_ASSET_URL+"?val=SUCCESS");
        mOnUiThread.loadUrlAndWaitForCompletion(fileUrl);
        assertEquals("SUCCESS", mOnUiThread.getTitle());
    }

    // verify anchors work correctly for android asset files
    public void testAndroidAssetAnchor() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        WebSettings settings = mOnUiThread.getSettings();
        settings.setJavaScriptEnabled(true);
        // test using an anchor
        String fileUrl = TestHtmlConstants.getFileUrl(TestHtmlConstants.ANCHOR_ASSET_URL+"#anchor");
        mOnUiThread.loadUrlAndWaitForCompletion(fileUrl);
        assertEquals("anchor", mOnUiThread.getTitle());
    }

    public void testEvaluateJavascript() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");

        EvaluateJsResultPollingCheck jsResult = new EvaluateJsResultPollingCheck("2");
        mOnUiThread.evaluateJavascript("1+1", jsResult);
        jsResult.run();

        jsResult = new EvaluateJsResultPollingCheck("9");
        mOnUiThread.evaluateJavascript("1+1; 4+5", jsResult);
        jsResult.run();

        final String EXPECTED_TITLE = "test";
        mOnUiThread.evaluateJavascript("document.title='" + EXPECTED_TITLE + "';", null);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals(EXPECTED_TITLE);
            }
        }.run();
    }

    // Verify Print feature can create a PDF file with a correct preamble.
    public void testPrinting() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mOnUiThread.loadDataAndWaitForCompletion("<html><head></head>" +
                "<body>foo</body></html>",
                "text/html", null);
        final PrintDocumentAdapter adapter =  mOnUiThread.createPrintDocumentAdapter();
        printDocumentStart(adapter);
        PrintAttributes attributes = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("foo", "bar", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        final WebViewStubActivity activity = getActivity();
        final File file = activity.getFileStreamPath(PRINTER_TEST_FILE);
        final ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.parseMode("w"));
        final FutureTask<Boolean> result =
                new FutureTask<Boolean>(new Callable<Boolean>() {
                            public Boolean call() {
                                return true;
                            }
                        });
        printDocumentLayout(adapter, null, attributes,
                new LayoutResultCallback() {
                    // Called on UI thread
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        savePrintedPage(adapter, descriptor, result);
                    }
                });
        try {
            result.get(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue(file.length() > 0);
            FileInputStream in = new FileInputStream(file);
            byte[] b = new byte[PDF_PREAMBLE.length()];
            in.read(b);
            String preamble = new String(b);
            assertEquals(PDF_PREAMBLE, preamble);
        } finally {
            // close the descriptor, if not closed already.
            descriptor.close();
            file.delete();
        }
    }

    private void savePrintedPage(final PrintDocumentAdapter adapter,
            final ParcelFileDescriptor descriptor, final FutureTask<Boolean> result) {
        adapter.onWrite(new PageRange[] {PageRange.ALL_PAGES}, descriptor,
                new CancellationSignal(),
                new WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        try {
                            descriptor.close();
                            result.run();
                        } catch (IOException ex) {
                            fail("Failed file operation: " + ex.toString());
                        }
                    }
                });
    }

    private void printDocumentStart(final PrintDocumentAdapter adapter) {
        mOnUiThread.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.onStart();
            }
        });
    }

    private void printDocumentLayout(final PrintDocumentAdapter adapter,
            final PrintAttributes oldAttributes, final PrintAttributes newAttributes,
            final LayoutResultCallback layoutResultCallback) {
        mOnUiThread.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.onLayout(oldAttributes, newAttributes, new CancellationSignal(),
                        layoutResultCallback, null);
            }
        });
    }

    private static class HrefCheckHandler extends Handler {
        private boolean mHadRecieved;

        private String mResultUrl;

        public HrefCheckHandler(Looper looper) {
            super(looper);
        }

        public boolean hasCalledHandleMessage() {
            return mHadRecieved;
        }

        public String getResultUrl() {
            return mResultUrl;
        }

        public void reset(){
            mResultUrl = null;
            mHadRecieved = false;
        }

        @Override
        public void handleMessage(Message msg) {
            mResultUrl = msg.getData().getString("url");
            mHadRecieved = true;
        }
    }

    private void moveFocusDown() throws Throwable {
        // send down key and wait for idle
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        // waiting for idle isn't always sufficient for the key to be fully processed
        Thread.sleep(500);
    }

    private void pollingCheckWebBackForwardList(final String currUrl, final int currIndex,
            final int size) {
        new PollingCheck() {
            @Override
            protected boolean check() {
                WebBackForwardList list = mWebView.copyBackForwardList();
                return checkWebBackForwardList(list, currUrl, currIndex, size);
            }
        }.run();
    }

    private boolean checkWebBackForwardList(WebBackForwardList list, String currUrl,
            int currIndex, int size) {
        return (list != null)
                && (list.getSize() == size)
                && (list.getCurrentIndex() == currIndex)
                && list.getItemAtIndex(currIndex).getUrl().equals(currUrl);
    }

    private void assertGoBackOrForwardBySteps(boolean expected, int steps) {
        // skip if steps equals to 0
        if (steps == 0)
            return;

        int start = steps > 0 ? 1 : steps;
        int end = steps > 0 ? steps : -1;

        // check all the steps in the history
        for (int i = start; i <= end; i++) {
            assertEquals(expected, mWebView.canGoBackOrForward(i));

            // shortcut methods for one step
            if (i == 1) {
                assertEquals(expected, mWebView.canGoForward());
            } else if (i == -1) {
                assertEquals(expected, mWebView.canGoBack());
            }
        }
    }

    private boolean isPictureFilledWithColor(Picture picture, int color) {
        if (picture.getWidth() == 0 || picture.getHeight() == 0)
            return false;

        Bitmap bitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(),
                Config.ARGB_8888);
        picture.draw(new Canvas(bitmap));

        for (int i = 0; i < bitmap.getWidth(); i ++) {
            for (int j = 0; j < bitmap.getHeight(); j ++) {
                if (color != bitmap.getPixel(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Waits at least MIN_SCROLL_WAIT_MS for scrolling to start. Once started,
     * scrolling is checked every SCROLL_WAIT_INTERVAL_MS for changes. Once
     * changes have stopped, the function exits. If no scrolling has happened
     * then the function exits after MIN_SCROLL_WAIT milliseconds.
     * @param previousScrollY The Y scroll position prior to waiting.
     */
    private void waitForScrollingComplete(int previousScrollY)
            throws InterruptedException {
        int scrollY = previousScrollY;
        // wait at least MIN_SCROLL_WAIT for something to happen.
        long noChangeMinWait = SystemClock.uptimeMillis() + MIN_SCROLL_WAIT_MS;
        boolean scrollChanging = false;
        boolean scrollChanged = false;
        boolean minWaitExpired = false;
        while (scrollChanging || (!scrollChanged && !minWaitExpired)) {
            Thread.sleep(SCROLL_WAIT_INTERVAL_MS);
            int oldScrollY = scrollY;
            scrollY = mOnUiThread.getScrollY();
            scrollChanging = (scrollY != oldScrollY);
            scrollChanged = (scrollY != previousScrollY);
            minWaitExpired = (SystemClock.uptimeMillis() > noChangeMinWait);
        }
    }

    // Note that this class is not thread-safe.
    class SslErrorWebViewClient extends WaitForLoadedClient {
        private boolean mWasOnReceivedSslErrorCalled;
        private String mErrorUrl;
        private int mErrorCode;

        public SslErrorWebViewClient() {
            super(mOnUiThread);
        }
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            mWasOnReceivedSslErrorCalled = true;
            mErrorUrl = error.getUrl();
            handler.proceed();
        }
        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            mErrorCode = errorCode;
        }
        public void resetWasOnReceivedSslErrorCalled() {
            mWasOnReceivedSslErrorCalled = false;
        }
        public boolean wasOnReceivedSslErrorCalled() {
            return mWasOnReceivedSslErrorCalled;
        }
        public String errorUrl() {
            return mErrorUrl;
        }
        public int onReceivedErrorCode() {
            return mErrorCode;
        }
    }

    // Modifies the default behavior of SslErrorWebViewClient to accept the request, and provide
    // certs.
    class ClientCertWebViewClient extends SslErrorWebViewClient {
        // User Actions
        public static final int PROCEED = 1;
        public static final int CANCEL = 2;
        public static final int IGNORE = 3;

        private int mClientCertRequests;
        private int mAction = PROCEED;
        private Principal[] mPrincipals;

        public int getClientCertRequestCount() {
            return mClientCertRequests;
        }

        public Principal[] getPrincipals() {
            return mPrincipals;
        }

        public void resetClientCertRequestCount() {
            mClientCertRequests = 0;
        }

        public void setAction(int action) {
            mAction = action;
        }

        @Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            mClientCertRequests++;
            mPrincipals = request.getPrincipals();
            if (mAction == IGNORE) {
                request.ignore();
                return;
            }
            if (mAction == CANCEL) {
                request.cancel();
                return;
            }
            if (mAction == PROCEED) {
                try {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    X509Certificate[] certChain =  new X509Certificate[] {
                            (X509Certificate) certFactory.generateCertificate(
                                    new ByteArrayInputStream(FAKE_RSA_USER_1)),
                            (X509Certificate) certFactory.generateCertificate(
                                    new ByteArrayInputStream(FAKE_RSA_CA_1))
                    };
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    PrivateKey key = keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(FAKE_RSA_KEY_1));
                    request.proceed(key, certChain);
                    return;
                } catch (Exception e) {
                    Log.e(LOGTAG,  "Fatal error" + e);
                }
            }
            throw new IllegalStateException("unknown action");
        }
    }

    private void pollingCheckForCanZoomIn() {
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return mOnUiThread.canZoomIn();
            }
        }.run();
    }

    final class ScaleChangedWebViewClient extends WaitForLoadedClient {
        private boolean mOnScaleChangedCalled = false;
        public ScaleChangedWebViewClient() {
            super(mOnUiThread);
        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            super.onScaleChanged(view, oldScale, newScale);
            synchronized (this) {
                mOnScaleChangedCalled = true;
            }
        }

        public void waitForScaleChanged() {
            new PollingCheck(TEST_TIMEOUT) {
                 @Override
                 protected boolean check() {
                     return onScaleChangedCalled();
                 }
            }.run();
            synchronized (this) {
                mOnScaleChangedCalled = false;
            }
        }

        public synchronized boolean onScaleChangedCalled() {
            return mOnScaleChangedCalled;
        }
    }
}
