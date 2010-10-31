package de.danielweisser.android.plaxosync.syncadapter;

import com.nullwire.trace.ExceptionHandler;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {
	private static final String TAG = "PlaxoSyncService";
	
    private static final Object sSyncAdapterLock = new Object();
    private static SyncAdapter sSyncAdapter = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
    	Log.v(TAG, "onCreate");
        synchronized (sSyncAdapterLock) {
        	ExceptionHandler.register(this, "http://www.danielweisser.de/android/server.php");
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
    	Log.v(TAG, "onBind");
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
