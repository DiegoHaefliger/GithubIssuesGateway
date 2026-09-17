package com.trade.triage.gateway.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FileSystemEvidenceStore implements EvidenceStore {

    private final Path diretorio;
    private final ObjectMapper objectMapper;

    public FileSystemEvidenceStore(EvidenceStoreProperties properties, ObjectMapper objectMapper) {
        this.diretorio = Path.of(properties.diretorio());
        this.objectMapper = objectMapper;
    }

    @Override
    public String store(EvidencePackage pacote) {
        try {
            Files.createDirectories(diretorio);
            Path destino = diretorio.resolve(pacote.id() + ".json");
            Path temporario = Files.createTempFile(diretorio, pacote.id(), ".tmp");
            Files.writeString(temporario, serializar(pacote), StandardCharsets.UTF_8);
            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return destino.toUri().toString();
        } catch (IOException exception) {
            throw new EvidenceStoreException("Falha ao gravar pacote de evidencia " + pacote.id(), exception);
        }
    }

    private String serializar(EvidencePackage pacote) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pacote);
        } catch (JsonProcessingException exception) {
            throw new EvidenceStoreException("Pacote de evidencia nao serializavel " + pacote.id(), exception);
        }
    }
}
