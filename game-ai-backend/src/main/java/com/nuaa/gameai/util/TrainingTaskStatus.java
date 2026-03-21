package com.nuaa.gameai.util;

public final class TrainingTaskStatus {
    public static final int PENDING = 0;
    public static final int QUEUED = 1;
    public static final int TRAINING = 2;
    public static final int PAUSED = 3;
    public static final int DONE = 4;
    public static final int FAILED = 5;
    public static final int STOPPED = 6;

    private TrainingTaskStatus() {
    }
}
