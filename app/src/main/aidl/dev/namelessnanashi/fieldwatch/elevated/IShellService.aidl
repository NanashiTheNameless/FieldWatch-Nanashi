package dev.namelessnanashi.fieldwatch.elevated;

// Runs inside the Shizuku user-service process as the adb shell user.
interface IShellService {
    // Shizuku calls this transaction id to tear the service down.
    void destroy() = 16777114;

    // Runs a command with sh -c. Returns the exit code, a newline, then the combined output.
    String exec(String command) = 1;
}
