/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.tvproviderperf;

import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.cts.util.CtsAndroidTestCase;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.RemoteException;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;
import com.android.cts.util.TimeoutReq;

import java.util.ArrayList;
import java.util.List;

/**
 * Test performance of TvProvider on a device. TvProvider typically handles hundreds of
 * thousands of records periodically, so it is desirable to have performance under a reasonable
 * bar.
 */
public class TvProviderPerfTest extends CtsAndroidTestCase {
    private static final int TRANSACTION_RUNS = 100;
    private static final int QUERY_RUNS = 10;

    private ContentResolver mContentResolver;
    private String mInputId;
    private boolean mHasTvInputFramework;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasTvInputFramework = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LIVE_TV);
        if (!mHasTvInputFramework) return;
        mContentResolver = getContext().getContentResolver();
        mInputId = TvContract.buildInputId(new ComponentName(getContext(), getClass()));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (!mHasTvInputFramework) return;
            mContentResolver.delete(Programs.CONTENT_URI, null, null);
            mContentResolver.delete(Channels.CONTENT_URI, null, null);
        } finally {
            super.tearDown();
        }
    }

    @TimeoutReq(minutes = 8)
    public void testChannels() throws Exception {
        if (!mHasTvInputFramework) return;
        double[] averages = new double[4];

        // Insert
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        final int TRANSACTION_SIZE = 1000;
        double[] applyBatchTimes = MeasureTime.measure(TRANSACTION_RUNS, new MeasureRun() {
            @Override
            public void run(int i) {
                operations.clear();
                for (int j = 0; j < TRANSACTION_SIZE; ++j) {
                    ContentValues values = new ContentValues();
                    values.put(Channels.COLUMN_INPUT_ID, mInputId);
                    values.put(Channels.COLUMN_SERVICE_TYPE,
                            Channels.SERVICE_TYPE_AUDIO_VIDEO);
                    values.put(Channels.COLUMN_TYPE, Channels.TYPE_OTHER);
                    operations.add(
                            ContentProviderOperation.newInsert(Channels.CONTENT_URI)
                            .withValues(values).build());
                }
                try {
                    mContentResolver.applyBatch(TvContract.AUTHORITY, operations);
                } catch (OperationApplicationException | RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        DeviceReportLog report = new DeviceReportLog();
        report.addValues("Elapsed time for insert: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[0] = Stat.getAverage(applyBatchTimes);

        // Update
        final String[] projection = { Channels._ID };
        try (final Cursor cursor = mContentResolver.query(Channels.CONTENT_URI,
                projection, null, null, null)) {
            applyBatchTimes = MeasureTime.measure(TRANSACTION_RUNS, new MeasureRun() {
                @Override
                public void run(int i) {
                    operations.clear();
                    for (int j = 0; j < TRANSACTION_SIZE && cursor.moveToNext(); ++j) {
                        Uri channelUri = TvContract.buildChannelUri(cursor.getLong(0));
                        String number = Integer.toString(i * TRANSACTION_SIZE + j);
                        operations.add(
                                ContentProviderOperation.newUpdate(channelUri)
                                .withValue(Channels.COLUMN_DISPLAY_NUMBER, number)
                                .build());
                    }
                    try {
                        mContentResolver.applyBatch(TvContract.AUTHORITY, operations);
                    } catch (OperationApplicationException | RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        report.addValues("Elapsed time for update: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[1] = Stat.getAverage(applyBatchTimes);

        // Query
        applyBatchTimes = MeasureTime.measure(QUERY_RUNS, new MeasureRun() {
            @Override
            public void run(int i) {
                int j = 0;
                try (Cursor cursor = mContentResolver.query(Channels.CONTENT_URI, null, null,
                        null, null)) {
                    while (cursor.moveToNext()) {
                        ++j;
                    }
                }
            }
        });
        report.addValues("Elapsed time for query: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[2] = Stat.getAverage(applyBatchTimes);

        // Delete
        applyBatchTimes = MeasureTime.measure(1, new MeasureRun() {
            @Override
            public void run(int i) {
                mContentResolver.delete(TvContract.buildChannelsUriForInput(mInputId), null, null);
            }
        });
        report.addValues("Elapsed time for delete: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[3] = Stat.getAverage(applyBatchTimes);

        report.addValues("Average elapsed time for (insert, update, query, delete): ",
                averages, ResultType.LOWER_BETTER, ResultUnit.MS);
        report.submit(getInstrumentation());
    }

    @TimeoutReq(minutes = 12)
    public void testPrograms() throws Exception {
        if (!mHasTvInputFramework) return;
        double[] averages = new double[6];

        // Prepare (insert channels)
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        final int TRANSACTION_SIZE = 1000;
        final int NUM_CHANNELS = 100;
        final List<Uri> channelUris = new ArrayList<>();

        operations.clear();
        for (int i = 0; i < NUM_CHANNELS; ++i) {
            ContentValues values = new ContentValues();
            values.put(Channels.COLUMN_INPUT_ID, mInputId);
            values.put(Channels.COLUMN_SERVICE_TYPE,
                    Channels.SERVICE_TYPE_AUDIO_VIDEO);
            values.put(Channels.COLUMN_TYPE, Channels.TYPE_OTHER);
            operations.add(
                    ContentProviderOperation.newInsert(Channels.CONTENT_URI)
                    .withValues(values).build());
        }
        try {
            ContentProviderResult[] results =
                    mContentResolver.applyBatch(TvContract.AUTHORITY, operations);
            for (ContentProviderResult result : results) {
                channelUris.add(result.uri);
            }
        } catch (OperationApplicationException | RemoteException e) {
            throw new RuntimeException(e);
        }

        // Insert
        double[] applyBatchTimes = MeasureTime.measure(NUM_CHANNELS, new MeasureRun() {
            @Override
            public void run(int i) {
                operations.clear();
                Uri channelUri = channelUris.get(i);
                long channelId = ContentUris.parseId(channelUri);
                for (int j = 0; j < TRANSACTION_SIZE; ++j) {
                    ContentValues values = new ContentValues();
                    values.put(Programs.COLUMN_CHANNEL_ID, channelId);
                    operations.add(
                            ContentProviderOperation.newInsert(Programs.CONTENT_URI)
                            .withValues(values).build());
                }
                try {
                    mContentResolver.applyBatch(TvContract.AUTHORITY, operations);
                } catch (OperationApplicationException | RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        DeviceReportLog report = new DeviceReportLog();
        report.addValues("Elapsed time for insert: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[0] = Stat.getAverage(applyBatchTimes);

        // Update
        final long PROGRAM_DURATION_MS = 60 * 1000;
        final String[] projection = { Programs._ID };
        applyBatchTimes = MeasureTime.measure(NUM_CHANNELS, new MeasureRun() {
            @Override
            public void run(int i) {
                Uri channelUri = channelUris.get(i);
                operations.clear();
                try (Cursor cursor = mContentResolver.query(
                        TvContract.buildProgramsUriForChannel(channelUri),
                        projection, null, null, null)) {
                    long startTimeMs = 0;
                    long endTimeMs = 0;
                    while (cursor.moveToNext()) {
                        Uri programUri = TvContract.buildProgramUri(cursor.getLong(0));
                        endTimeMs += PROGRAM_DURATION_MS;
                        operations.add(
                                ContentProviderOperation.newUpdate(programUri)
                                .withValue(Programs.COLUMN_START_TIME_UTC_MILLIS, startTimeMs)
                                .withValue(Programs.COLUMN_END_TIME_UTC_MILLIS, endTimeMs)
                                .build());
                        startTimeMs = endTimeMs;
                    }
                }
                try {
                    mContentResolver.applyBatch(TvContract.AUTHORITY, operations);
                } catch (OperationApplicationException | RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        report.addValues("Elapsed time for update: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[1] = Stat.getAverage(applyBatchTimes);

        // Query
        applyBatchTimes = MeasureTime.measure(QUERY_RUNS, new MeasureRun() {
            @Override
            public void run(int i) {
                int j = 0;
                try (Cursor cursor = mContentResolver.query(Programs.CONTENT_URI, null, null,
                        null, null)) {
                    while (cursor.moveToNext()) {
                        ++j;
                    }
                }
            }
        });
        report.addValues("Elapsed time for query: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[2] = Stat.getAverage(applyBatchTimes);

        // Query programs with selection
        applyBatchTimes = MeasureTime.measure(QUERY_RUNS, new MeasureRun() {
            @Override
            public void run(int i) {
                Uri channelUri = channelUris.get(i);
                int j = 0;
                try (Cursor cursor = mContentResolver.query(
                        TvContract.buildProgramsUriForChannel(
                                channelUri, 0,
                                PROGRAM_DURATION_MS * TRANSACTION_SIZE / 2),
                        null, null, null, null)) {
                    while (cursor.moveToNext()) {
                        ++j;
                    }
                }
            }
        });
        report.addValues("Elapsed time for query with selection: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[3] = Stat.getAverage(applyBatchTimes);

        // Delete programs
        applyBatchTimes = MeasureTime.measure(NUM_CHANNELS, new MeasureRun() {
            @Override
            public void run(int i) {
                Uri channelUri = channelUris.get(i);
                mContentResolver.delete(
                        TvContract.buildProgramsUriForChannel(
                                channelUri,
                                PROGRAM_DURATION_MS * TRANSACTION_SIZE / 2,
                                PROGRAM_DURATION_MS * TRANSACTION_SIZE),
                        null, null);
            }
        });
        report.addValues("Elapsed time for delete programs: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[4] = Stat.getAverage(applyBatchTimes);

        // Delete channels
        applyBatchTimes = MeasureTime.measure(NUM_CHANNELS, new MeasureRun() {
            @Override
            public void run(int i) {
                Uri channelUri = channelUris.get(i);
                mContentResolver.delete(channelUri, null, null);
            }
        });
        report.addValues("Elapsed time for delete channels: ",
                applyBatchTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
        averages[5] = Stat.getAverage(applyBatchTimes);

        report.addValues("Average elapsed time for (insert, update, query, "
                + "query with selection, delete channels, delete programs): ",
                averages, ResultType.LOWER_BETTER, ResultUnit.MS);
        report.submit(getInstrumentation());
    }
}
