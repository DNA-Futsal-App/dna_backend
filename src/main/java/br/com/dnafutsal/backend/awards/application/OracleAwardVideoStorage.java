package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeletePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
public class OracleAwardVideoStorage {

    private final AwardRegistrationProperties properties;

    private volatile ObjectStorageClient client;

    public OracleAwardVideoStorage(
            AwardRegistrationProperties properties
    ) {
        this.properties = properties;
    }

    public WriteTicket createWriteTicket(
            String objectName,
            String ticketName,
            Instant expiresAt
    ) {
        try {
            CreatePreauthenticatedRequestDetails details =
                    CreatePreauthenticatedRequestDetails.builder()
                            .name(
                                    ticketName
                            )
                            .bucketListingAction(
                                    PreauthenticatedRequest.BucketListingAction.Deny
                            )
                            .objectName(
                                    objectName
                            )
                            .accessType(
                                    CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite
                            )
                            .timeExpires(
                                    Date.from(
                                            expiresAt
                                    )
                            )
                            .build();

            CreatePreauthenticatedRequestResponse response =
                    client()
                            .createPreauthenticatedRequest(
                                    CreatePreauthenticatedRequestRequest.builder()
                                            .namespaceName(
                                                    properties.ociNamespace()
                                            )
                                            .bucketName(
                                                    properties.ociBucket()
                                            )
                                            .createPreauthenticatedRequestDetails(
                                                    details
                                            )
                                            .build()
                            );

            PreauthenticatedRequest request =
                    response.getPreauthenticatedRequest();

            String accessUri =
                    request.getAccessUri();

            String uploadUrl =
                    accessUri.startsWith(
                            "http://"
                    )
                            || accessUri.startsWith(
                            "https://"
                    )
                            ? accessUri
                            : properties.effectiveOciEndpoint()
                            + (accessUri.startsWith(
                            "/"
                    )
                            ? accessUri
                            : "/"
                            + accessUri);

            return new WriteTicket(
                    request.getId(),
                    uploadUrl,
                    expiresAt
            );
        } catch (RuntimeException exception) {
            throw Errors.dependencyUnavailable(
                    "AWARD_OBJECT_STORAGE_UNAVAILABLE",
                    "Não foi possível liberar o envio do vídeo para o armazenamento.",
                    exception,
                    Map.of()
            );
        }
    }

    public long objectSize(
            String objectName
    ) {
        try {
            HeadObjectResponse response =
                    client()
                            .headObject(
                                    HeadObjectRequest.builder()
                                            .namespaceName(
                                                    properties.ociNamespace()
                                            )
                                            .bucketName(
                                                    properties.ociBucket()
                                            )
                                            .objectName(
                                                    objectName
                                            )
                                            .build()
                            );

            Long length =
                    response.getContentLength();

            if (length == null
                    || length <= 0) {
                throw Errors.badRequest(
                        "AWARD_UPLOAD_EMPTY",
                        "O arquivo enviado está vazio."
                );
            }

            return length;
        } catch (br.com.dnafutsal.backend.common.BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw Errors.dependencyUnavailable(
                    "AWARD_OBJECT_STORAGE_UNAVAILABLE",
                    "Não foi possível validar o arquivo enviado.",
                    exception,
                    Map.of()
            );
        }
    }

    public void download(
            String objectName,
            Path target
    ) {
        try {
            GetObjectResponse response =
                    client()
                            .getObject(
                                    GetObjectRequest.builder()
                                            .namespaceName(
                                                    properties.ociNamespace()
                                            )
                                            .bucketName(
                                                    properties.ociBucket()
                                            )
                                            .objectName(
                                                    objectName
                                            )
                                            .build()
                            );

            try (InputStream input =
                         response.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException | RuntimeException exception) {
            throw Errors.dependencyUnavailable(
                    "AWARD_OBJECT_STORAGE_DOWNLOAD_FAILED",
                    "Não foi possível preparar o vídeo enviado para validação.",
                    exception,
                    Map.of()
            );
        }
    }

    public void uploadProcessedVideo(
            String objectName,
            Path source
    ) {
        try (InputStream input =
                     Files.newInputStream(
                             source
                     )) {
            client()
                    .putObject(
                            PutObjectRequest.builder()
                                    .namespaceName(
                                            properties.ociNamespace()
                                    )
                                    .bucketName(
                                            properties.ociBucket()
                                    )
                                    .objectName(
                                            objectName
                                    )
                                    .contentLength(
                                            Files.size(
                                                    source
                                            )
                                    )
                                    .contentType(
                                            "video/mp4"
                                    )
                                    .putObjectBody(
                                            input
                                    )
                                    .build()
                    );
        } catch (IOException | RuntimeException exception) {
            throw Errors.dependencyUnavailable(
                    "AWARD_OBJECT_STORAGE_UPLOAD_FAILED",
                    "Não foi possível armazenar o vídeo processado.",
                    exception,
                    Map.of()
            );
        }
    }

    public void deleteObjectQuietly(
            String objectName
    ) {
        if (objectName == null
                || objectName.isBlank()) {
            return;
        }

        try {
            client()
                    .deleteObject(
                            DeleteObjectRequest.builder()
                                    .namespaceName(
                                            properties.ociNamespace()
                                    )
                                    .bucketName(
                                            properties.ociBucket()
                                    )
                                    .objectName(
                                            objectName
                                    )
                                    .build()
                    );
        } catch (RuntimeException ignored) {
        }
    }

    public void deleteParQuietly(
            String parId
    ) {
        if (parId == null
                || parId.isBlank()) {
            return;
        }

        try {
            client()
                    .deletePreauthenticatedRequest(
                            DeletePreauthenticatedRequestRequest.builder()
                                    .namespaceName(
                                            properties.ociNamespace()
                                    )
                                    .bucketName(
                                            properties.ociBucket()
                                    )
                                    .parId(
                                            parId
                                    )
                                    .build()
                    );
        } catch (RuntimeException ignored) {
        }
    }

    private ObjectStorageClient client() {
        ObjectStorageClient current =
                client;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (client != null) {
                return client;
            }

            requireConfiguration();

            final byte[] privateKey;

            try {
                privateKey =
                        Base64.getDecoder()
                                .decode(
                                        properties.ociPrivateKeyBase64()
                                                .trim()
                                );
            } catch (IllegalArgumentException exception) {
                throw Errors.dependencyUnavailable(
                        "AWARD_OBJECT_STORAGE_NOT_CONFIGURED",
                        "A chave privada da Oracle está em formato Base64 inválido.",
                        exception,
                        Map.of()
                );
            }

            SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder providerBuilder =
                    SimpleAuthenticationDetailsProvider.builder()
                            .tenantId(
                                    properties.ociTenancyOcid()
                            )
                            .userId(
                                    properties.ociUserOcid()
                            )
                            .fingerprint(
                                    properties.ociFingerprint()
                            )
                            .privateKeySupplier(
                                    () -> new ByteArrayInputStream(
                                            privateKey
                                    )
                            )
                            .region(
                                    Region.fromRegionId(
                                            properties.ociRegion()
                                    )
                            );

            if (properties.ociPrivateKeyPassphrase()
                    != null
                    && !properties.ociPrivateKeyPassphrase()
                    .isBlank()) {
                providerBuilder.passphraseCharacters(
                        properties.ociPrivateKeyPassphrase()
                                .toCharArray()
                );
            }

            ObjectStorageClient created =
                    ObjectStorageClient.builder()
                            .build(
                                    providerBuilder.build()
                            );

            String endpoint =
                    properties.effectiveOciEndpoint();

            if (!endpoint.isBlank()) {
                created.setEndpoint(
                        endpoint
                );
            }

            client = created;

            return created;
        }
    }

    private void requireConfiguration() {
        java.util.List<String> missing =
                properties.missingObjectStorageConfiguration();

        if (!missing.isEmpty()) {
            throw Errors.dependencyUnavailable(
                    "AWARD_OBJECT_STORAGE_NOT_CONFIGURED",
                    "Configuração incompleta do Oracle Object Storage. Campos ausentes: "
                            + String.join(", ", missing)
            );
        }
    }

    @PreDestroy
    void close() {
        ObjectStorageClient current =
                client;

        if (current != null) {
            current.close();
        }
    }

    public record WriteTicket(
            String parId,
            String uploadUrl,
            Instant expiresAt
    ) {
    }
}
