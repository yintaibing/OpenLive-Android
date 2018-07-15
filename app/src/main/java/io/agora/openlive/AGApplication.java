package io.agora.openlive;

import android.app.Application;

import io.agora.openlive.model.WorkerThread;

public class AGApplication extends Application {

    public static AGApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    private WorkerThread mWorkerThread;

    public synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(getApplicationContext());
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    public synchronized WorkerThread getWorkerThread() {
        return mWorkerThread;
    }

    public synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }
}
