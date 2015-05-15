/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.cts;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import java.io.IOException;
import java.util.Vector;

public class ResourceManagerTestActivityBase extends Activity {
    protected String TAG;
    private static final int IFRAME_INTERVAL = 10;  // 10 seconds between I-frames
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;

    private Vector<MediaCodec> mCodecs = new Vector<MediaCodec>();

    private class TestCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.d(TAG, "onInputBufferAvailable " + codec.toString());
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.d(TAG, "onOutputBufferAvailable " + codec.toString());
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.d(TAG, "onError " + codec.toString() + " errorCode " + e.getErrorCode());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged " + codec.toString());
        }
    }

    private MediaCodec.Callback mCallback = new TestCodecCallback();

    private static MediaFormat getTestFormat(VideoCapabilities vcaps) {
        int maxWidth = vcaps.getSupportedWidths().getUpper();
        int maxHeight = vcaps.getSupportedHeightsFor(maxWidth).getUpper();
        int maxBitrate = vcaps.getBitrateRange().getUpper();
        int maxFramerate = vcaps.getSupportedFrameRatesFor(maxWidth, maxHeight)
                .getUpper().intValue();

        MediaFormat format = MediaFormat.createVideoFormat(MIME, maxWidth, maxHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, maxBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFramerate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        return format;
    }

    private MediaCodecInfo getTestCodecInfo() {
        // Use avc decoder for testing.
        boolean isEncoder = false;

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (info.isEncoder() != isEncoder) {
                continue;
            }
            CodecCapabilities caps;
            try {
                caps = info.getCapabilitiesForType(MIME);
            } catch (IllegalArgumentException e) {
                // mime is not supported
                continue;
            }
            return info;
        }

        return null;
    }

    protected int allocateCodecs(int max) {
        MediaCodecInfo info = getTestCodecInfo();
        if (info == null) {
            // skip the test
            return 0;
        }

        String name = info.getName();
        VideoCapabilities vcaps = info.getCapabilitiesForType(MIME).getVideoCapabilities();
        MediaFormat format = getTestFormat(vcaps);
        for (int i = 0; i < max; ++i) {
            try {
                Log.d(TAG, "Create codec " + name + " #" + i);
                MediaCodec codec = MediaCodec.createByCodecName(name);
                codec.setCallback(mCallback);
                Log.d(TAG, "Configure codec " + format);
                codec.configure(format, null, null, 0);
                Log.d(TAG, "Start codec " + format);
                codec.start();
                mCodecs.add(codec);
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "IllegalArgumentException " + e.getMessage());
                break;
            } catch (IOException e) {
                Log.d(TAG, "IOException " + e.getMessage());
                break;
            } catch (MediaCodec.CodecException e) {
                Log.d(TAG, "CodecException 0x" + Integer.toHexString(e.getErrorCode()));
                break;
            }
        }

        return mCodecs.size();
    }

    private void doUseCodecs() {
        int current = 0;
        try {
            for (current = 0; current < mCodecs.size(); ++current) {
                mCodecs.get(current).getName();
            }
        } catch (MediaCodec.CodecException e) {
            Log.d(TAG, "useCodecs got CodecException 0x" + Integer.toHexString(e.getErrorCode()));
            if (e.getErrorCode() == MediaCodec.CodecException.ERROR_RECLAIMED) {
                Log.d(TAG, "Remove codec " + current + " from the list");
                mCodecs.remove(current);
                setResult(Activity.RESULT_OK);
                finish();
            }
            return;
        }
    }

    private Thread mWorkerThread;
    protected void useCodecs() {
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    doUseCodecs();
                }
            }
        });
        mWorkerThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        super.onDestroy();

        for (int i = 0; i < mCodecs.size(); ++i) {
            mCodecs.get(i).release();
        }
    }
}
