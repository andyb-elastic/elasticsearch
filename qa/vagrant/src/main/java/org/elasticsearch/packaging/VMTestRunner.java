package org.elasticsearch.packaging;

import org.junit.runner.JUnitCore;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Ensures that the current JVM is running on a virtual machine before delegating to {@link JUnitCore}. We just check for the existence
 * of a special file that we create during VM provisioning.
 */
public class VMTestRunner {
    public static void main(String[] args) {
        if (Files.exists(Paths.get("/is_vagrant_vm"))) {
            JUnitCore.main(args);
        } else {
            throw new RuntimeException("This filesystem does not have an expected marker file indicating it's a virtual machine. These " +
                "tests should only run in a virtual machine because they're destructive.");
        }
    }
}
