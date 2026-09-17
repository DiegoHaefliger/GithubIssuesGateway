package com.trade.triage.registry.source;

import com.trade.triage.registry.RegistryLoadException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemRegistrySource implements RegistrySource {

    private final Path arquivo;

    public FileSystemRegistrySource(Path arquivo) {
        this.arquivo = arquivo;
    }

    @Override
    public String fetchRawJson() {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RegistryLoadException("Falha ao ler registro em " + arquivo, exception);
        }
    }

    @Override
    public String describe() {
        return "file:" + arquivo.toAbsolutePath();
    }
}
