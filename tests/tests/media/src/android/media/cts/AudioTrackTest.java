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

package android.media.cts;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.test.AndroidTestCase;
import android.util.Log;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;

public class AudioTrackTest extends AndroidTestCase {
    private String TAG = "AudioTrackTest";
    private final long WAIT_MSEC = 200;
    private final int OFFSET_DEFAULT = 0;
    private final int OFFSET_NEGATIVE = -10;

    private void log(String testName, String message) {
        Log.v(TAG, "[" + testName + "] " + message);
    }

    private void loge(String testName, String message) {
        Log.e(TAG, "[" + testName + "] " + message);
    }

    // -----------------------------------------------------------------
    // private class to hold test results
    private static class TestResults {
        public boolean mResult = false;
        public String mResultLog = "";

        public TestResults(boolean b, String s) {
            mResult = b;
            mResultLog = s;
        }
    }

    // -----------------------------------------------------------------
    // generic test methods
    public TestResults constructorTestMultiSampleRate(
    // parameters tested by this method
            int _inTest_streamType, int _inTest_mode, int _inTest_config, int _inTest_format,
            // parameter-dependent expected results
            int _expected_stateForMode) {

        int[] testSampleRates = { 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 };
        String failedRates = "Failure for rate(s): ";
        boolean localRes, finalRes = true;

        for (int i = 0; i < testSampleRates.length; i++) {
            AudioTrack track = null;
            try {
                track = new AudioTrack(_inTest_streamType, testSampleRates[i], _inTest_config,
                        _inTest_format, AudioTrack.getMinBufferSize(testSampleRates[i],
                                _inTest_config, _inTest_format), _inTest_mode);
            } catch (IllegalArgumentException iae) {
                Log.e("MediaAudioTrackTest", "[ constructorTestMultiSampleRate ] exception at SR "
                        + testSampleRates[i] + ": \n" + iae);
                localRes = false;
            }
            if (track != null) {
                localRes = (track.getState() == _expected_stateForMode);
                track.release();
            } else {
                localRes = false;
            }

            if (!localRes) {
                // log the error for the test runner
                failedRates += Integer.toString(testSampleRates[i]) + "Hz ";
                // log the error for logcat
                log("constructorTestMultiSampleRate", "failed to construct "
                        + "AudioTrack(streamType="
                        + _inTest_streamType
                        + ", sampleRateInHz="
                        + testSampleRates[i]
                        + ", channelConfig="
                        + _inTest_config
                        + ", audioFormat="
                        + _inTest_format
                        + ", bufferSizeInBytes="
                        + AudioTrack.getMinBufferSize(testSampleRates[i], _inTest_config,
                                AudioFormat.ENCODING_PCM_16BIT) + ", mode=" + _inTest_mode);
                // mark test as failed
                finalRes = false;
            }
        }
        return new TestResults(finalRes, failedRates);
    }

    // -----------------------------------------------------------------
    // AUDIOTRACK TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // AudioTrack constructor and AudioTrack.getMinBufferSize(...) for 16bit PCM
    // ----------------------------------

    // Test case 1: constructor for streaming AudioTrack, mono, 16bit at misc
    // valid sample rates
    public void testConstructorMono16MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorMono16MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 2: constructor for streaming AudioTrack, stereo, 16bit at misc
    // valid sample rates
    public void testConstructorStereo16MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorStereo16MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 3: constructor for static AudioTrack, mono, 16bit at misc valid
    // sample rates
    public void testConstructorMono16MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorMono16MusicStatic: " + res.mResultLog, res.mResult);
    }

    // Test case 4: constructor for static AudioTrack, stereo, 16bit at misc
    // valid sample rates
    public void testConstructorStereo16MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorStereo16MusicStatic: " + res.mResultLog, res.mResult);
    }

    // -----------------------------------------------------------------
    // AudioTrack constructor and AudioTrack.getMinBufferSize(...) for 8bit PCM
    // ----------------------------------

    // Test case 1: constructor for streaming AudioTrack, mono, 8bit at misc
    // valid sample rates
    public void testConstructorMono8MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorMono8MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 2: constructor for streaming AudioTrack, stereo, 8bit at misc
    // valid sample rates
    public void testConstructorStereo8MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorStereo8MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 3: constructor for static AudioTrack, mono, 8bit at misc valid
    // sample rates
    public void testConstructorMono8MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorMono8MusicStatic: " + res.mResultLog, res.mResult);
    }

    // Test case 4: constructor for static AudioTrack, stereo, 8bit at misc
    // valid sample rates
    public void testConstructorStereo8MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorStereo8MusicStatic: " + res.mResultLog, res.mResult);
    }

    // -----------------------------------------------------------------
    // AudioTrack constructor for all stream types
    // ----------------------------------

    // Test case 1: constructor for all stream types
    public void testConstructorStreamType() throws Exception {
        // constants for test
        final int TYPE_TEST_SR = 22050;
        final int TYPE_TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TYPE_TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TYPE_TEST_MODE = AudioTrack.MODE_STREAM;
        final int[] STREAM_TYPES = { AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_RING,
                AudioManager.STREAM_SYSTEM, AudioManager.STREAM_VOICE_CALL };
        final String[] STREAM_NAMES = { "STREAM_ALARM", "STREAM_MUSIC", "STREAM_NOTIFICATION",
                "STREAM_RING", "STREAM_SYSTEM", "STREAM_VOICE_CALL" };

        boolean localTestRes = true;
        AudioTrack track = null;
        // test: loop constructor on all stream types
        for (int i = 0; i < STREAM_TYPES.length; i++) {
            try {
                // -------- initialization --------------
                track = new AudioTrack(STREAM_TYPES[i], TYPE_TEST_SR, TYPE_TEST_CONF,
                        TYPE_TEST_FORMAT, AudioTrack.getMinBufferSize(TYPE_TEST_SR, TYPE_TEST_CONF,
                                TYPE_TEST_FORMAT), TYPE_TEST_MODE);
            } catch (IllegalArgumentException iae) {
                loge("testConstructorStreamType", "exception for stream type " + STREAM_NAMES[i]
                        + ": " + iae);
                localTestRes = false;
            }
            // -------- test --------------
            if (track != null) {
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    localTestRes = false;
                    Log.e("MediaAudioTrackTest",
                            "[ testConstructorStreamType ] failed for stream type "
                                    + STREAM_NAMES[i]);
                }
                // -------- tear down --------------
                track.release();
            } else {
                localTestRes = false;
            }
        }

        assertTrue("testConstructorStreamType", localTestRes);
    }

    // -----------------------------------------------------------------
    // Playback head position
    // ----------------------------------

    // Test case 1: getPlaybackHeadPosition() at 0 after initialization
    public void testPlaybackHeadPositionAfterInit() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterInit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT), TEST_MODE);
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: getPlaybackHeadPosition() increases after play()
    public void testPlaybackHeadPositionIncrease() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionIncrease";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        log(TEST_NAME, "position =" + track.getPlaybackHeadPosition());
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() > 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: getPlaybackHeadPosition() is 0 after flush();
    public void testPlaybackHeadPositionAfterFlush() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterFlush";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        track.flush();
        log(TEST_NAME, "position =" + track.getPlaybackHeadPosition());
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: getPlaybackHeadPosition() is 0 after stop();
    public void testPlaybackHeadPositionAfterStop() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterStop";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_LOOP_CNT = 10;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        int count = 0;
        int pos;
        do {
            Thread.sleep(WAIT_MSEC);
            pos = track.getPlaybackHeadPosition();
            count++;
        } while((pos != 0) && (count < TEST_LOOP_CNT));
        log(TEST_NAME, "position =" + pos + ", read count ="+count);
        assertTrue(TEST_NAME, pos == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: getPlaybackHeadPosition() is > 0 after play(); pause();
    public void testPlaybackHeadPositionAfterPause() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterPause";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        track.pause();
        int pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position =" + pos);
        assertTrue(TEST_NAME, pos > 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: getPlaybackHeadPosition() remains 0 after pause(); flush(); play();
    public void testPlaybackHeadPositionAfterFlushAndPlay() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterFlushAndPlay";
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_SR = AudioTrack.getNativeOutputSampleRate(TEST_STREAM_TYPE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        track.pause();

        int pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after pause =" + pos);
        assertTrue(TEST_NAME, pos > 0);

        track.flush();
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after flush =" + pos);
        assertTrue(TEST_NAME, pos == 0);

        track.play();
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after play =" + pos);
        assertTrue(TEST_NAME, pos == 0);

        Thread.sleep(100);
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after 100 ms sleep =" + pos);
        assertTrue(TEST_NAME, pos == 0);
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Playback properties
    // ----------------------------------

    // Common code for the testSetStereoVolume* and testSetVolume* tests
    private void testSetVolumeCommon(String testName, float vol, boolean isStereo) throws Exception {
        // constants for test
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        if (isStereo) {
            // TODO to really test this, do a pan instead of using same value for left and right
            assertTrue(testName, track.setStereoVolume(vol, vol) == AudioTrack.SUCCESS);
        } else {
            assertTrue(testName, track.setVolume(vol) == AudioTrack.SUCCESS);
        }
        // -------- tear down --------------
        track.release();
    }

    // Test case 1: setStereoVolume() with max volume returns SUCCESS
    public void testSetStereoVolumeMax() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMax";
        float maxVol = AudioTrack.getMaxVolume();
        testSetVolumeCommon(TEST_NAME, maxVol, true /*isStereo*/);
    }

    // Test case 2: setStereoVolume() with min volume returns SUCCESS
    public void testSetStereoVolumeMin() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMin";
        float minVol = AudioTrack.getMinVolume();
        testSetVolumeCommon(TEST_NAME, minVol, true /*isStereo*/);
    }

    // Test case 3: setStereoVolume() with mid volume returns SUCCESS
    public void testSetStereoVolumeMid() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMid";
        float midVol = (AudioTrack.getMaxVolume() - AudioTrack.getMinVolume()) / 2;
        testSetVolumeCommon(TEST_NAME, midVol, true /*isStereo*/);
    }

    // Test case 4: setPlaybackRate() with half the content rate returns SUCCESS
    public void testSetPlaybackRate() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRate";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        assertTrue(TEST_NAME, track.setPlaybackRate((int) (TEST_SR / 2)) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: setPlaybackRate(0) returns bad value error
    public void testSetPlaybackRateZero() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateZero";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setPlaybackRate(0) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setPlaybackRate() accepts values twice the output sample
    // rate
    public void testSetPlaybackRateTwiceOutputSR() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateTwiceOutputSR";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int outputSR = AudioTrack.getNativeOutputSampleRate(TEST_STREAM_TYPE);
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        assertTrue(TEST_NAME, track.setPlaybackRate(2 * outputSR) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: setPlaybackRate() and retrieve value, should be the same for
    // half the content SR
    public void testSetGetPlaybackRate() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetGetPlaybackRate";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        track.setPlaybackRate((int) (TEST_SR / 2));
        assertTrue(TEST_NAME, track.getPlaybackRate() == (int) (TEST_SR / 2));
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: setPlaybackRate() invalid operation if track not initialized
    public void testSetPlaybackRateUninit() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateUninit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        assertEquals(TEST_NAME, AudioTrack.ERROR_INVALID_OPERATION,
                track.setPlaybackRate(TEST_SR / 2));
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: setVolume() with max volume returns SUCCESS
    public void testSetVolumeMax() throws Exception {
        final String TEST_NAME = "testSetVolumeMax";
        float maxVol = AudioTrack.getMaxVolume();
        testSetVolumeCommon(TEST_NAME, maxVol, false /*isStereo*/);
    }

    // Test case 10: setVolume() with min volume returns SUCCESS
    public void testSetVolumeMin() throws Exception {
        final String TEST_NAME = "testSetVolumeMin";
        float minVol = AudioTrack.getMinVolume();
        testSetVolumeCommon(TEST_NAME, minVol, false /*isStereo*/);
    }

    // Test case 11: setVolume() with mid volume returns SUCCESS
    public void testSetVolumeMid() throws Exception {
        final String TEST_NAME = "testSetVolumeMid";
        float midVol = (AudioTrack.getMaxVolume() - AudioTrack.getMinVolume()) / 2;
        testSetVolumeCommon(TEST_NAME, midVol, false /*isStereo*/);
    }

    // -----------------------------------------------------------------
    // Playback progress
    // ----------------------------------

    // Test case 1: setPlaybackHeadPosition() on playing track
    public void testSetPlaybackHeadPositionPlaying() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionPlaying";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        assertTrue(TEST_NAME,
                track.setPlaybackHeadPosition(10) == AudioTrack.ERROR_INVALID_OPERATION);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: setPlaybackHeadPosition() on stopped track
    public void testSetPlaybackHeadPositionStopped() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionStopped";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.stop();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_STOPPED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackHeadPosition(10));
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: setPlaybackHeadPosition() on paused track
    public void testSetPlaybackHeadPositionPaused() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionPaused";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.pause();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_PAUSED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackHeadPosition(10));
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: setPlaybackHeadPosition() beyond what has been written
    public void testSetPlaybackHeadPositionTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // make up a frame index that's beyond what has been written: go from
        // buffer size to frame
        // count (given the audio track properties), and add 77.
        int frameIndexTooFar = (2 * minBuffSize / 2) + 77;
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.stop();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_STOPPED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.ERROR_BAD_VALUE,
                track.setPlaybackHeadPosition(frameIndexTooFar));
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: setLoopPoints() fails for MODE_STREAM
    public void testSetLoopPointsStream() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStream";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(2, 50, 2) == AudioTrack.ERROR_INVALID_OPERATION);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setLoopPoints() fails start > end
    public void testSetLoopPointsStartAfterEnd() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStartAfterEnd";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(50, 0, 2) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setLoopPoints() success
    public void testSetLoopPointsSuccess() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsSuccess";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(0, 50, 2) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: setLoopPoints() fails with loop length bigger than content
    public void testSetLoopPointsLoopTooLong() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsLoopTooLong";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(10, dataSizeInFrames + 20, 2) ==
            AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: setLoopPoints() fails with start beyond what can be written
    // for the track
    public void testSetLoopPointsStartTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStartTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;// 16bit data
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME,
                track.setLoopPoints(dataSizeInFrames + 20, dataSizeInFrames + 50, 2) ==
                    AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: setLoopPoints() fails with end beyond what can be written
    // for the track
    public void testSetLoopPointsEndTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsEndTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;// 16bit data
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int loopCount = 2;
        assertTrue(TEST_NAME,
                track.setLoopPoints(dataSizeInFrames - 10, dataSizeInFrames + 50, loopCount) ==
                    AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Audio data supply
    // ----------------------------------

    // Test case 1: write() fails when supplying less data (bytes) than declared
    public void testWriteByteOffsetTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteOffsetTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int offset = 10;
        assertTrue(TEST_NAME, track.write(data, offset, data.length) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: write() fails when supplying less data (shorts) than
    // declared
    public void testWriteShortOffsetTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortOffsetTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int offset = 10;
        assertTrue(TEST_NAME, track.write(data, offset, data.length)
                                                            == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: write() fails when supplying less data (bytes) than declared
    public void testWriteByteSizeTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteSizeTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length + 10)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: write() fails when supplying less data (shorts) than
    // declared
    public void testWriteShortSizeTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortSizeTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length + 10)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: write() fails with negative offset
    public void testWriteByteNegativeOffset() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteNegativeOffset";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_NEGATIVE, data.length - 10) ==
            AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: write() fails with negative offset
    public void testWriteShortNegativeOffset() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortNegativeOffset";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME,
        track.write(data, OFFSET_NEGATIVE, data.length - 10) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: write() fails with negative size
    public void testWriteByteNegativeSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteNegativeSize";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int dataLength = -10;
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, dataLength)
                                                    == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: write() fails with negative size
    public void testWriteShortNegativeSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortNegativeSize";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int dataLength = -10;
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, dataLength)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: write() succeeds and returns the size that was written for
    // 16bit
    public void testWriteByte() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByte";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length) == data.length);
        // -------- tear down --------------
        track.release();
    }

    // Test case 10: write() succeeds and returns the size that was written for
    // 16bit
    public void testWriteShort() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShort";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length) == data.length);
        track.flush();
        // -------- tear down --------------
        track.release();
    }

    // Test case 11: write() succeeds and returns the size that was written for
    // 8bit
    public void testWriteByte8bit() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByte8bit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertEquals(TEST_NAME, data.length, track.write(data, OFFSET_DEFAULT, data.length));
        // -------- tear down --------------
        track.release();
    }

    // Test case 12: write() succeeds and returns the size that was written for
    // 8bit
    public void testWriteShort8bit() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShort8bit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertEquals(TEST_NAME, data.length, track.write(data, OFFSET_DEFAULT, data.length));
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Getters
    // ----------------------------------

    // Test case 1: getMinBufferSize() return ERROR_BAD_VALUE if SR < 4000
    public void testGetMinBufferSizeTooLowSR() throws Exception {
        // constant for test
        final String TEST_NAME = "testGetMinBufferSizeTooLowSR";
        final int TEST_SR = 3999;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;

        // -------- initialization & test --------------
        assertTrue(TEST_NAME, AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT) ==
            AudioTrack.ERROR_BAD_VALUE);
    }

    // Test case 2: getMinBufferSize() return ERROR_BAD_VALUE if SR > 48000
    public void testGetMinBufferSizeTooHighSR() throws Exception {
        // constant for test
        final String TEST_NAME = "testGetMinBufferSizeTooHighSR";
        final int TEST_SR = 48001;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;

        // -------- initialization & test --------------
        assertTrue(TEST_NAME, AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT) ==
            AudioTrack.ERROR_BAD_VALUE);
    }

    public void testAudioTrackProperties() throws Exception {
        // constants for test
        final String TEST_NAME = "testAudioTrackProperties";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        MockAudioTrack track = new MockAudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF,
                TEST_FORMAT, 2 * minBuffSize, TEST_MODE);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        assertEquals(TEST_NAME, TEST_FORMAT, track.getAudioFormat());
        assertEquals(TEST_NAME, TEST_CONF, track.getChannelConfiguration());
        assertEquals(TEST_NAME, TEST_SR, track.getSampleRate());
        assertEquals(TEST_NAME, TEST_STREAM_TYPE, track.getStreamType());
        final int hannelCount = 1;
        assertEquals(hannelCount, track.getChannelCount());
        final int notificationMarkerPosition = 0;
        assertEquals(TEST_NAME, notificationMarkerPosition, track.getNotificationMarkerPosition());
        final int markerInFrames = 2;
        assertEquals(TEST_NAME, AudioTrack.SUCCESS,
                track.setNotificationMarkerPosition(markerInFrames));
        assertEquals(TEST_NAME, markerInFrames, track.getNotificationMarkerPosition());
        final int positionNotificationPeriod = 0;
        assertEquals(TEST_NAME, positionNotificationPeriod, track.getPositionNotificationPeriod());
        final int periodInFrames = 2;
        assertEquals(TEST_NAME, AudioTrack.SUCCESS,
                track.setPositionNotificationPeriod(periodInFrames));
        assertEquals(TEST_NAME, periodInFrames, track.getPositionNotificationPeriod());
        track.setState(AudioTrack.STATE_NO_STATIC_DATA);
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.setState(AudioTrack.STATE_UNINITIALIZED);
        assertEquals(TEST_NAME, AudioTrack.STATE_UNINITIALIZED, track.getState());
        int frameCount = 2 * minBuffSize;
        if (TEST_CONF == AudioFormat.CHANNEL_CONFIGURATION_STEREO) {
            frameCount /= 2;
        }
        if (TEST_FORMAT == AudioFormat.ENCODING_PCM_16BIT) {
            frameCount /= 2;
        }
        assertTrue(TEST_NAME, track.getNativeFrameCount() >= frameCount);
    }

    public void testReloadStaticData() throws Exception {
        // constants for test
        final String TEST_NAME = "testReloadStaticData";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int bufferSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        byte data[] = createSoundDataInByteArray(bufferSize, TEST_SR, 1024);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                bufferSize, TEST_MODE);
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, bufferSize);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        Thread.sleep(WAIT_MSEC);
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.reloadStaticData());
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        // -------- tear down --------------
        track.release();
    }

    public static byte[] createSoundDataInByteArray(int bufferSamples, final int sampleRate,
            final double frequency) {
        final double rad = 2 * Math.PI * frequency / sampleRate;
        byte[] vai = new byte[bufferSamples];
        for (int j = 0; j < vai.length; j++) {
            int unsigned =  (int)(Math.sin(j * rad) * Byte.MAX_VALUE) + Byte.MAX_VALUE & 0xFF;
            vai[j] = (byte) unsigned;
        }
        return vai;
    }

    public static short[] createSoundDataInShortArray(int bufferSamples, final int sampleRate,
            final double frequency) {
        final double rad = 2 * Math.PI * frequency / sampleRate;
        short[] vai = new short[bufferSamples];
        for (int j = 0; j < vai.length; j++) {
            vai[j] = (short) (Math.sin(j * rad) * Short.MAX_VALUE);
        }
        return vai;
    }

    public static float[] createSoundDataInFloatArray(int bufferSamples, final int sampleRate,
            final double frequency) {
        final double rad = 2 * Math.PI * frequency / sampleRate;
        float[] vaf = new float[bufferSamples];
        for (int j = 0; j < bufferSamples; j++) {
            vaf[j] = (float) (Math.sin(j * rad));
        }
        return vaf;
    }

    public void testPlayStreamData() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlayStreamData";
        final int TEST_FORMAT_ARRAY[] = {  // should hear 6 tones played 3 times
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
        };
        final int TEST_SR_ARRAY[] = {
                22050,
                44100,
                48000,
        };
        final int TEST_CONF_ARRAY[] = {
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.CHANNEL_OUT_STEREO,
        };
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            double frequency = 800; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CONF : TEST_CONF_ARRAY) {
                    // -------- initialization --------------
                    int minBufferSize = AudioTrack.getMinBufferSize(TEST_SR,
                            TEST_CONF, TEST_FORMAT); // in bytes
                    int bufferSamples = 12 * minBufferSize
                            / AudioFormat.getBytesPerSample(TEST_FORMAT);
                    AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR,
                            TEST_CONF, TEST_FORMAT, minBufferSize, TEST_MODE);
                    assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
                    boolean hasPlayed = false;
                    int written = 0;

                    // -------- test --------------
                    switch (TEST_FORMAT) {
                    case AudioFormat.ENCODING_PCM_8BIT: {
                        byte data[] = createSoundDataInByteArray(
                                bufferSamples, TEST_SR,
                                frequency);
                        while (written < data.length) {
                            int ret = track.write(data, written,
                                    Math.min(data.length - written, minBufferSize));
                            assertTrue(TEST_NAME, ret >= 0);
                            written += ret;
                            if (!hasPlayed) {
                                track.play();
                                hasPlayed = true;
                            }
                        }
                        } break;
                    case AudioFormat.ENCODING_PCM_16BIT: {
                        short data[] = createSoundDataInShortArray(
                                bufferSamples, TEST_SR,
                                frequency);
                        while (written < data.length) {
                            int ret = track.write(data, written,
                                    Math.min(data.length - written, minBufferSize));
                            assertTrue(TEST_NAME, ret >= 0);
                            written += ret;
                            if (!hasPlayed) {
                                track.play();
                                hasPlayed = true;
                            }
                        }
                        } break;
                    case AudioFormat.ENCODING_PCM_FLOAT: {
                        float data[] = createSoundDataInFloatArray(
                                bufferSamples, TEST_SR,
                                frequency);
                        while (written < data.length) {
                            int ret = track.write(data, written,
                                    Math.min(data.length - written, minBufferSize),
                                    AudioTrack.WRITE_BLOCKING);
                            assertTrue(TEST_NAME, ret >= 0);
                            written += ret;
                            if (!hasPlayed) {
                                track.play();
                                hasPlayed = true;
                            }
                        }
                        } break;
                    }

                    Thread.sleep(WAIT_MSEC);
                    track.stop();
                    Thread.sleep(WAIT_MSEC);
                    // -------- tear down --------------
                    track.release();
                    frequency += 200; // increment test tone frequency
                }
            }
        }
    }

    public void testPlayStreamByteBuffer() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlayStreamByteBuffer";
        final int TEST_FORMAT_ARRAY[] = {  // should hear 2 tones played 3 times
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
        };
        final int TEST_SR_ARRAY[] = {
                48000,
        };
        final int TEST_CONF_ARRAY[] = {
                AudioFormat.CHANNEL_OUT_STEREO,
        };
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            double frequency = 800; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CONF : TEST_CONF_ARRAY) {
                    for (int useDirect = 0; useDirect < 2; ++useDirect) {
                        // -------- initialization --------------
                        int minBufferSize = AudioTrack.getMinBufferSize(TEST_SR,
                                TEST_CONF, TEST_FORMAT); // in bytes
                        int bufferSize = 12 * minBufferSize;
                        int bufferSamples = bufferSize
                                / AudioFormat.getBytesPerSample(TEST_FORMAT);
                        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR,
                                TEST_CONF, TEST_FORMAT, minBufferSize, TEST_MODE);
                        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
                        boolean hasPlayed = false;
                        int written = 0;
                        ByteBuffer bb = (useDirect == 1)
                                ? ByteBuffer.allocateDirect(bufferSize)
                                        : ByteBuffer.allocate(bufferSize);
                        bb.order(java.nio.ByteOrder.nativeOrder());

                        // -------- test --------------
                        switch (TEST_FORMAT) {
                        case AudioFormat.ENCODING_PCM_8BIT: {
                            byte data[] = createSoundDataInByteArray(
                                    bufferSamples, TEST_SR,
                                    frequency);
                            bb.put(data);
                            bb.flip();
                            } break;
                        case AudioFormat.ENCODING_PCM_16BIT: {
                            short data[] = createSoundDataInShortArray(
                                    bufferSamples, TEST_SR,
                                    frequency);
                            ShortBuffer sb = bb.asShortBuffer();
                            sb.put(data);
                            bb.limit(sb.limit() * 2);
                            } break;
                        case AudioFormat.ENCODING_PCM_FLOAT: {
                            float data[] = createSoundDataInFloatArray(
                                    bufferSamples, TEST_SR,
                                    frequency);
                            FloatBuffer fb = bb.asFloatBuffer();
                            fb.put(data);
                            bb.limit(fb.limit() * 4);
                            } break;
                        }

                        while (written < bufferSize) {
                            int ret = track.write(bb,
                                    Math.min(bufferSize - written, minBufferSize),
                                    AudioTrack.WRITE_BLOCKING);
                            assertTrue(TEST_NAME, ret >= 0);
                            written += ret;
                            if (!hasPlayed) {
                                track.play();
                                hasPlayed = true;
                            }
                        }

                        Thread.sleep(WAIT_MSEC);
                        track.stop();
                        Thread.sleep(WAIT_MSEC);
                        // -------- tear down --------------
                        track.release();
                        frequency += 200; // increment test tone frequency
                    }
                }
            }
        }
    }

    public void testGetTimestamp() throws Exception {
        // constants for test
        final String TEST_NAME = "testGetTimestamp";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_LOOP_CNT = 10;

        final int MILLIS_PER_SEC = 1000;
        final int NANOS_PER_MILLI = 1000000;

        // -------- initialization --------------
        final int BYTES_PER_FRAME = 2;
        final int FRAMES_PER_100_MILLIS =
                AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        final int FRAMES_PER_SEC = FRAMES_PER_100_MILLIS * 10;
        byte[] data = new byte[FRAMES_PER_100_MILLIS * BYTES_PER_FRAME];
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                 FRAMES_PER_SEC * BYTES_PER_FRAME, TEST_MODE);
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);

        long framesWritten = 0, framesSeen = 0, framesPresented = 0;
        long lastFramesPresented = 0, lastFrameTime = 0, lastOutputFrequency = FRAMES_PER_SEC;
        AudioTimestamp timestamp = new AudioTimestamp();

        for (int i = 0; i < TEST_LOOP_CNT; i++) {
            for (int j = 0; j < FRAMES_PER_SEC; j += FRAMES_PER_100_MILLIS) {
                track.write(data, 0, data.length);
                framesWritten += FRAMES_PER_100_MILLIS;
            }

            track.play();
            Thread.sleep(MILLIS_PER_SEC);
            track.pause();

            framesSeen = track.getPlaybackHeadPosition();
            assertTrue(TEST_NAME, framesWritten >= framesSeen);

            assertTrue(TEST_NAME, track.getTimestamp(timestamp));
            framesPresented = timestamp.framePosition;
            assertTrue(TEST_NAME, framesSeen >= framesPresented);

            // check output frequency
            long outFrequency = framesPresented - lastFramesPresented;
            long freqDiff = Math.abs(FRAMES_PER_SEC - outFrequency);
            assertTrue(TEST_NAME, freqDiff < FRAMES_PER_100_MILLIS);

            // check output frequency jitter
            freqDiff = Math.abs(outFrequency - lastOutputFrequency);
            assertTrue(TEST_NAME, freqDiff < FRAMES_PER_100_MILLIS);

            lastFramesPresented = framesPresented;
            lastOutputFrequency = outFrequency;

            long frameTime = timestamp.nanoTime;
            assertTrue(TEST_NAME, frameTime >= lastFrameTime);
            lastFrameTime = frameTime;

            long curTime = System.nanoTime();
            assertTrue(TEST_NAME, curTime >= frameTime);
            assertTrue(TEST_NAME, curTime - frameTime < 100 * NANOS_PER_MILLI);
        }
        track.stop();
        // -------- tear down --------------
        track.release();
    }

/* Do not run in JB-MR1. will be re-opened in the next platform release.
    public void testResourceLeakage() throws Exception {
        final int BUFFER_SIZE = 600 * 1024;
        ByteBuffer data = ByteBuffer.allocate(BUFFER_SIZE);
        for (int i = 0; i < 10; i++) {
            Log.i(TAG, "testResourceLeakage round " + i);
            data.rewind();
            AudioTrack track = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                                              44100,
                                              AudioFormat.CHANNEL_OUT_STEREO,
                                              AudioFormat.ENCODING_PCM_16BIT,
                                              data.capacity(),
                                              AudioTrack.MODE_STREAM);
            assertTrue(track != null);
            track.write(data.array(), 0, data.capacity());
            track.play();
            Thread.sleep(100);
            track.stop();
            track.release();
        }
    }
*/

    private class MockAudioTrack extends AudioTrack {

        public MockAudioTrack(int streamType, int sampleRateInHz, int channelConfig,
                int audioFormat, int bufferSizeInBytes, int mode) throws IllegalArgumentException {
            super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
        }

        public void setState(int state) {
            super.setState(state);
        }

        public int getNativeFrameCount() {
            return super.getNativeFrameCount();
        }
    }

}
