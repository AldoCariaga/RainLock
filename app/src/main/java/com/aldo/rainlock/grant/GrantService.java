package com.aldo.rainlock.grant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Runs in a process Shizuku starts with shell rights, where pm grant is allowed. */
public final class GrantService extends IGrantService.Stub {
    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String grant(String packageName, String permission) {
        try {
            Process process = new ProcessBuilder("pm", "grant", packageName, permission)
                    .redirectErrorStream(true)
                    .start();
            String output = read(process.getInputStream());
            return process.waitFor() == 0 ? null : output;
        } catch (IOException | InterruptedException e) {
            return e.toString();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toString().trim();
    }
}
