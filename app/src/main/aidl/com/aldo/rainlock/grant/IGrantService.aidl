package com.aldo.rainlock.grant;

interface IGrantService {
    // Shizuku calls this transaction code to stop the service
    void destroy() = 16777114;

    // Returns null on success, or what went wrong
    String grant(String packageName, String permission) = 1;
}
