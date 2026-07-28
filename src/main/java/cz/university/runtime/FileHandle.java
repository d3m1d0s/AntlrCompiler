package cz.university.runtime;

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

