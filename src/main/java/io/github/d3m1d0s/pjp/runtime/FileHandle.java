package io.github.d3m1d0s.pjp.runtime;

/**
 * Runtime value of a file variable, kept as its own type so file values are
 * not mistaken for strings on the stack. The string form, file(name), is what
 * write prints for a file value.
 */
public class FileHandle {
    private final String name;

    public FileHandle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "file(" + name + ")";
    }
}

