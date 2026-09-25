package com.aldo.rainlock;

import android.app.Activity;
import android.os.Bundle;

/** Locks and closes straight away. The tile opens it in Basic Mode, since opening an activity closes the panel. */
public final class LockActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LockController.lock(this);
        finish();
    }
}
