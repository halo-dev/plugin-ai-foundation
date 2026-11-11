package run.halo.aifoundation.provider.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Records the official API documentation behind a deterministic provider contract fixture. */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ProviderContractSource {

    String provider();

    String officialDocumentation();

    String retrievedAt();
}
