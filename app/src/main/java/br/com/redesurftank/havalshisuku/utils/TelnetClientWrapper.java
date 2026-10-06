package br.com.redesurftank.havalshisuku.utils;

import android.util.Log;

import org.apache.commons.net.telnet.TelnetClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TelnetClientWrapper {

    private static final String TAG = "TelnetClientWrapper";

    private TelnetClient telnetClient;
    private InputStream in;
    private OutputStream out;

    public void connect(String host, int port) throws IOException {
        telnetClient = new TelnetClient();
        telnetClient.setConnectTimeout(1000);
        telnetClient.connect(host, port);
        in = telnetClient.getInputStream();
        out = telnetClient.getOutputStream();
    }

    private String stripAnsi(String s) {
        return s.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    public String executeCommand(String command) throws IOException, InterruptedException {
        if (telnetClient == null) {
            throw new IllegalStateException("Connection not established");
        }
        out.write((command + "\r\n").getBytes());
        out.flush();

        byte[] bufferBytes = new byte[1024];
        StringBuilder buffer = new StringBuilder();
        StringBuilder clean = new StringBuilder();
        List<String> lines = new ArrayList<>();
        String prompt = ":/ #";
        long startTime = System.currentTimeMillis();
        long timeout = 5000;

        while (System.currentTimeMillis() - startTime < timeout) {
            if (in.available() > 0) {
                int bytesRead = in.read(bufferBytes);
                if (bytesRead == -1) break;
                buffer.append(new String(bufferBytes, 0, bytesRead));
                Log.d(TAG, "Actual buffer: " + buffer);

                int newlineIndex;
                while ((newlineIndex = buffer.indexOf("\n")) != -1) {
                    String line = buffer.substring(0, newlineIndex).trim();
                    lines.add(line);
                    buffer.delete(0, newlineIndex + 1);
                }
            }

            String potentialLast = buffer.toString().trim();
            boolean promptInBuffer = potentialLast.endsWith(prompt);
            boolean promptFoundLocal = promptInBuffer || (!lines.isEmpty() && lines.get(lines.size() - 1).endsWith(prompt));

            if (promptFoundLocal) {
                int startIndex = 0;
                String fullEcho = prompt + " " + command;
                if (lines.contains(fullEcho)) {
                    startIndex = lines.indexOf(fullEcho) + 1;
                } else if (lines.contains(command)) {
                    startIndex = lines.indexOf(command) + 1;
                }
                int endIndex = promptInBuffer ? lines.size() : lines.size() - 1;
                for (int i = startIndex; i < endIndex; i++) {
                    String line = lines.get(i);
                    if (line.isEmpty() || line.endsWith(prompt)) continue;
                    clean.append(line).append("\n");
                }
                Log.w(TAG, "Prompt found. Lines: " + String.join(", ", lines));
                return clean.toString().trim();
            }

            Thread.sleep(10);
        }

        Log.e(TAG, "Timeout waiting for prompt. Lines: " + String.join(", ", lines) + " Buffer: " + buffer);
        throw new IOException("Timeout waiting for prompt");
    }


    public void disconnect() throws IOException {
        if (telnetClient != null) {
            telnetClient.disconnect();
        }
    }
}